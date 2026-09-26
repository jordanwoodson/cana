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
                if (before.getInt("standbyBucket") != 5)
                    command("am", "set-standby-bucket", "--user", user, pkg, before.getInt("standbyBucket").toString())
            }
            "metered", "metered_reapply" -> {
                val appId = before.getInt("appId")
                PrivacyPolicy.uid(record.userId, appId)
                if (before.has("desiredMetered")) command("cana-privacy", "metered-desired-set", pkg, user, appId.toString(), before.getBoolean("desiredMetered").toString())
                command("cana-privacy", "metered-set", pkg, user, appId.toString(), before.getInt("meteredPolicy").toString())
            }
            "metered_desired_restore" -> command("cana-privacy", "metered-desired-set", pkg, user, before.getInt("appId").toString(), before.getBoolean("desiredMetered").toString())
            "network_desired_restore" -> command("cana-privacy", "desired-set", pkg, user, before.getInt("appId").toString(), before.getBoolean("desiredBlock").toString())
            "network", "network_reapply" -> {
                if (before.has("networkRule")) {
                    PrivacyPolicy.uid(record.userId, before.getInt("appId"))
                    val rule = before.getInt("networkRule")
                    require(rule in 0..2)
                    if (before.has("desiredBlock")) command("cana-privacy", "desired-set", pkg, user, before.getInt("appId").toString(), before.getBoolean("desiredBlock").toString())
                    command("cana-privacy", "network-set", pkg, user, before.getInt("appId").toString(), rule.toString())
                } else {
                    require(record.userId == 0) { "Legacy networking records are only safe in the system user" }
                    command("cmd", "connectivity", "set-package-networking-enabled", before.getBoolean("networkEnabled").toString(), pkg)
                }
            }
            "permissions" -> {
                val permissions = before.getJSONArray("permissions")
                for (index in 0 until permissions.length()) {
                    val permission = permissions.getJSONObject(index)
                    val name = permission.getString("name")
                    val flags = permission.getInt("flags")
                    if (!PrivacyPolicy.mutablePermission(flags)) continue
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
                            "captive_portal_https_url", "captive_portal_fallback_url", "captive_portal_other_fallback_urls", "captive_portal_use_https",
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
            if (steps.any { it.second.commands.any { command -> command.firstOrNull() == "cana-privacy" } }) {
                append("cana_privacy() {\n  cana_apk=\$(pm path --user 0 io.github.jordanwoodson.cana | sed -n 's/^package://p' | head -n 1)\n")
                append("  [ -r \"\$cana_apk\" ] || { printf '%s\\n' 'Install Cana to use per-profile network recovery.' >&2; return 1; }\n")
                append("  CLASSPATH=\"\$cana_apk\" app_process /system/bin io.github.samolego.canta.ops.PrivacyRecovery \"\$@\"\n}\n")

            }
            for ((record, plan) in steps) {
                fun comment(text: String) { append("# ").append(text.replace('\n', ' ').replace('\r', ' ')).append('\n') }
                comment("${record.action}: ${record.packageName} (user ${record.userId})")
                plan.notes.forEach { comment("MANUAL: $it") }
                plan.commands.forEach { command ->
                    val argv = if (command.firstOrNull() == "cana-privacy") listOf("cana_privacy") + command.drop(1) else command
                    append("run ").append(argv.joinToString(" ", transform = ::quote)).append('\n')
                }
            }
            append("printf 'Recovery finished: %s failed commands; %s manual steps.\\n' \"\$failures\" \"\$manual_steps\"\n")
            append("[ \"\$failures\" -eq 0 ] || exit 1\n[ \"\$manual_steps\" -eq 0 ] || exit 2\n")
        }
    }
}
