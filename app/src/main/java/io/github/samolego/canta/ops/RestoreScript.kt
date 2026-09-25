package io.github.samolego.canta.ops

import io.github.samolego.canta.data.proto.OperationRecord
import org.json.JSONObject

data class RestorePlan(val commands: List<List<String>>, val notes: List<String> = emptyList())

object RestoreScript {
    fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    fun plan(record: OperationRecord): RestorePlan = try {
        require(record.userId >= 0)
        val before = JSONObject(record.previousState)
        val after = runCatching { JSONObject(record.afterState) }.getOrDefault(JSONObject())
        val user = record.userId.toString()
        val pkg = record.packageName
        val commands = mutableListOf<List<String>>()
        val notes = mutableListOf<String>()
        fun command(vararg args: String) { commands += args.toList() }
        fun enabled(target: String) {
            val verb = when (before.getInt("enabledSetting")) {
                0 -> "default-state"; 1 -> "enable"; 2 -> "disable"; 3 -> "disable-user"; 4 -> "disable-until-used"
                else -> error("Unknown enabled state")
            }
            command("pm", verb, "--user", user, target)
        }
        when (record.action) {
            "uninstall", "uninstall_keep_data" -> {
                if (before.getBoolean("installed")) {
                    command("cmd", "package", "install-existing", "--user", user, pkg)
                    if (before.has("enabledSetting")) enabled(pkg)
                    if (!before.optBoolean("systemApp", true) && !after.optBoolean("apkAvailable", true))
                        notes += "Android removed the last APK copy of $pkg. Reinstall its APK to recover the app."
                    if (record.action == "uninstall")
                        notes += "Uninstall may have deleted data for $pkg. Installation can be restored; deleted app data cannot be recovered."
                }
            }
            "reinstall" -> if (!before.getBoolean("installed")) command("pm", "uninstall", "--user", user, pkg)
            "remove_updates" -> if (!before.getBoolean("installed") && after.optBoolean("installed")) {
                command("pm", "uninstall", "--user", user, pkg)
            }
            "disable", "enable" -> enabled(pkg)
            "component" -> enabled(before.getString("component"))
            "suspend", "unsuspend" -> command("pm", if (before.getBoolean("suspended")) "suspend" else "unsuspend", "--user", user, pkg)
            "background" -> {
                command("cmd", "appops", "set", "--user", user, pkg, "RUN_ANY_IN_BACKGROUND", before.getString("runAnyInBackground"))
                command("am", "set-standby-bucket", "--user", user, pkg, before.getInt("standbyBucket").toString())
            }
            "metered" -> {
                val appId = before.getInt("appId")
                require(appId in 0 until 100_000)
                val uid = (record.userId.toLong() * 100_000 + appId).also { require(it <= Int.MAX_VALUE) }.toString()
                val policy = before.getInt("meteredPolicy")
                command("cmd", "netpolicy", "remove", "restrict-background-blacklist", uid)
                command("cmd", "netpolicy", "remove", "restrict-background-whitelist", uid)
                if (policy and 1 != 0) command("cmd", "netpolicy", "add", "restrict-background-blacklist", uid)
                if (policy and 4 != 0) command("cmd", "netpolicy", "add", "restrict-background-whitelist", uid)
            }
            "network" -> {
                require(record.userId == 0) { "This Android interface only resolves packages in the system user" }
                command("cmd", "connectivity", "set-package-networking-enabled", before.getBoolean("networkEnabled").toString(), pkg)
            }
            "permissions" -> {
                val permissions = before.getJSONArray("permissions")
                for (index in 0 until permissions.length()) {
                    val permission = permissions.getJSONObject(index)
                    val name = permission.getString("name")
                    val flags = permission.getInt("flags")
                    command("pm", "clear-permission-flags", "--user", user, pkg, name, "user-fixed", "user-set")
                    command("pm", if (permission.getBoolean("granted")) "grant" else "revoke", "--user", user, pkg, name)
                    val originalFlags = buildList { if (flags and 1 != 0) add("user-set"); if (flags and 2 != 0) add("user-fixed") }
                    if (originalFlags.isNotEmpty()) commands += listOf("pm", "set-permission-flags", "--user", user, pkg, name) + originalFlags
                }
            }
            "system" -> {
                before.optJSONObject("settings")?.let { settings ->
                    for (key in settings.keys()) {
                        require(key in setOf("private_dns_mode", "private_dns_specifier", "captive_portal_mode", "captive_portal_http_url",
                            "captive_portal_https_url", "captive_portal_fallback_url", "captive_portal_other_fallback_urls",
                            "wifi_scan_always_enabled", "ble_scan_always_enabled", "mobile_data_always_on"))
                        if (settings.isNull(key)) command("settings", "delete", "global", key)
                        else command("settings", "put", "global", key, settings.getString(key))
                    }
                }
                if (before.has("dataSaver")) command("cmd", "netpolicy", "set", "restrict-background", before.getBoolean("dataSaver").toString())
                require(commands.isNotEmpty()) { "Missing previous system state" }
            }
            "self_grant_secure_settings" -> command("pm", if (before.getBoolean("granted")) "grant" else "revoke",
                "--user", user, pkg, "android.permission.WRITE_SECURE_SETTINGS")
            "self_grant_usage_stats" -> {
                command("cmd", "appops", "set", "--user", user, pkg, "GET_USAGE_STATS", before.getString("appOpMode"))
            }
            else -> error("Unknown history action ${record.action}")
        }
        if (before.optBoolean("updatedSystemApp") && !after.optBoolean("updatedSystemApp", true)) {
            notes += "Removed update APK files for $pkg cannot be restored automatically. Reinstall the previous update APK if needed."
        }
        val essential = SafetyPolicy.protectedPackages("io.github.jordanwoodson.cana")
        if (pkg in essential && commands.any { it.firstOrNull() == "pm" && it.getOrNull(1) in setOf("uninstall", "disable", "disable-user", "disable-until-used", "suspend") }) {
            RestorePlan(emptyList(), notes + "Refused a destructive recovery action on essential package $pkg.")
        } else RestorePlan(commands, notes)
    } catch (e: Exception) {
        RestorePlan(emptyList(), listOf("Cannot safely reconstruct ${record.action} for ${record.packageName}: ${e.message}"))
    }

    fun generate(records: List<OperationRecord>): String {
        val steps = records.asReversed().filter { it.changed || !it.completed }.map { it to plan(it) }
        val manual = steps.sumOf { it.second.notes.size }
        return buildString {
            append("#!/system/bin/sh\n# Cana recovery: run with adb shell sh /data/local/tmp/cana-restore.sh\n")
            append("# Restores captured values in reverse history order. Review manual steps below.\n")
            append("failures=0\nmanual_steps=$manual\n")
            append("run() {\n  \"\$@\"\n  result=\$?\n  if [ \"\$result\" -ne 0 ]; then\n    failures=\$((failures + 1))\n    printf '%s\\n' \"Recovery command failed (\$result): \$*\" >&2\n  fi\n}\n")
            for ((record, plan) in steps) {
                fun comment(text: String) { append("# ").append(text.replace('\n', ' ').replace('\r', ' ')).append('\n') }
                comment("${record.action}: ${record.packageName} (user ${record.userId})")
                plan.notes.forEach { comment("MANUAL: $it") }
                plan.commands.forEach { append("run ").append(it.joinToString(" ", transform = ::quote)).append('\n') }
            }
            append("printf 'Recovery finished: %s failed commands; %s manual steps.\\n' \"\$failures\" \"\$manual_steps\"\n")
            append("[ \"\$failures\" -eq 0 ] || exit 1\n[ \"\$manual_steps\" -eq 0 ] || exit 2\n")
        }
    }
}
