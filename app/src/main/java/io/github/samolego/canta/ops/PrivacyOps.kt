package io.github.samolego.canta.ops

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import io.github.samolego.canta.R
import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.data.PrivacyStore
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils as Packages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PrivacyAction(val key: String, val title: Int) {
    PERMISSIONS("permissions", R.string.privacy_permissions), BACKGROUND("background", R.string.privacy_background),
    METERED("metered", R.string.privacy_metered), NETWORK("network", R.string.privacy_network);
}
data class PrivacyState(val values: Map<PrivacyAction, JSONObject>, val errors: Map<PrivacyAction, String>,
    val sharedPackages: List<String>, val desiredBlock: Boolean, val desiredMetered: Boolean = false)

class PrivacyOps(private val context: Context, private val shell: ShellRunner,
    private val history: HistoryStore, private val desired: PrivacyStore,
    private val safety: SafetyInspector, private val journal: OperationJournal,
) {
    private suspend fun exec(vararg args: String): String {
        val result = shell.exec(args.toList())
        check(result.success) { result.message }
        return result.stdout.trim()
    }
    private fun info(pkg: String, user: Int): PackageInfo =
        Packages.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS, user)
            ?: error(context.getString(R.string.operation_package_missing))
    private fun shared(pkg: String, user: Int): List<String> {
        val appId = info(pkg, user).applicationInfo!!.uid % 100_000
        PrivacyPolicy.uid(user, appId)
        return Packages.getInstalledPackages(0, user).filter { it.applicationInfo!!.uid % 100_000 == appId }
            .map { it.packageName }.sorted()
    }
    suspend fun assess(pkg: String, user: Int): Map<String, SafetyAssessment> = withContext(Dispatchers.IO) {
        safety.inspect(shared(pkg, user), user)
    }
    private suspend fun checkSafety(pkg: String, user: Int, approved: Set<String>) {
        val peers = shared(pkg, user)
        check(peers.size == 1 || "shared-uid" in approved) { context.getString(R.string.privacy_shared_uid, peers.joinToString()) }
        val reports = safety.inspect(peers, user)
        reports.forEach { (name, report) ->
            check(report.permits(approved)) { report.error ?: if (report.protected)
                context.getString(R.string.safety_protected, name)
                else context.getString(R.string.safety_confirmation_required, name) }
        }
    }
    suspend fun load(pkg: String, user: Int): PrivacyState = withContext(Dispatchers.IO) {
        val values = mutableMapOf<PrivacyAction, JSONObject>()
        val errors = mutableMapOf<PrivacyAction, String>()
        for (action in PrivacyAction.entries) {
            try { values[action] = snapshot(pkg, user, action) }
            catch (e: Exception) { errors[action] = (e.cause ?: e).message.orEmpty() }
        }
        PrivacyState(values, errors, runCatching { shared(pkg, user) }.getOrDefault(emptyList()),
            desired.blocks.first().any { it.packageName == pkg && it.userId == user },
            desired.meteredBlocks.first().any { it.packageName == pkg && it.userId == user })
    }
    suspend fun snapshot(pkg: String, user: Int, action: PrivacyAction): JSONObject = withContext(Dispatchers.IO) {
        val info = info(pkg, user)
        val appId = info.applicationInfo!!.uid % 100_000
        val uid = PrivacyPolicy.uid(user, appId)
        val state = JSONObject().put("appId", appId)
        when (action) {
            PrivacyAction.PERMISSIONS -> {
                val permissions = JSONArray()
                val runtime = info.requestedPermissions.orEmpty().withIndex().filter { (_, name) ->
                    val detail = context.packageManager.getPermissionInfo(name, 0)
                    detail.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE == PermissionInfo.PROTECTION_DANGEROUS
                }
                val flagsByName = if (runtime.isEmpty()) JSONObject() else JSONObject(exec("cana-privacy", "permission-flags-list",
                    pkg, "$user", runtime.joinToString(",") { it.value }))
                for ((index, name) in runtime) {
                    val flags = flagsByName.getInt(name)
                    permissions.put(JSONObject().put("name", name).put("flags", flags)
                        .put("granted", info.requestedPermissionsFlags!![index] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0))
                }
                state.put("permissions", permissions)
            }
            PrivacyAction.BACKGROUND -> {
                val raw = exec("cmd", "appops", "get", "--user", "$user", pkg, "RUN_ANY_IN_BACKGROUND")
                state.put("runAnyInBackground", PrivacyPolicy.appOpMode(raw)).put("uidOverride", raw.contains("Uid mode:"))
                    .put("standbyBucket", exec("am", "get-standby-bucket", "--user", "$user", pkg).toInt())
            }
            PrivacyAction.METERED -> {
                val denied = uid in PrivacyPolicy.uidList(exec("cmd", "netpolicy", "list", "restrict-background-blacklist"))
                val allowed = uid in PrivacyPolicy.uidList(exec("cmd", "netpolicy", "list", "restrict-background-whitelist"))
                state.put("meteredPolicy", (if (denied) 1 else 0) or (if (allowed) 4 else 0))
                    .put("desiredMetered", desired.meteredBlocks.first().any { it.packageName == pkg && it.userId == user })
            }
            PrivacyAction.NETWORK -> {
                val live = JSONObject(exec("cana-privacy", "network-get", pkg, "$user", "$appId"))
                state.put("networkRule", live.getInt("networkRule")).put("chainEnabled", live.getBoolean("chainEnabled"))
                state.put("desiredBlock", desired.blocks.first().any { it.packageName == pkg && it.userId == user })
            }
        }
        state
    }

    suspend fun restrict(pkg: String, user: Int, action: PrivacyAction, approved: Set<String> = emptySet(),
        batchId: String = UUID.randomUUID().toString(),
        reconcileAppId: Int? = null,
    ): OperationResult = journal.run(pkg, user, if (reconcileAppId == null) action.key else "${action.key}_reapply", batchId,
        snapshot = { snapshot(pkg, user, action) }) { before ->
        if (reconcileAppId != null) {
            if (!before.optBoolean(if (action == PrivacyAction.METERED) "desiredMetered" else "desiredBlock")) return@run OperationResult(true, context.getString(R.string.operation_skipped), skipped = true)
            check(before.getInt("appId") == reconcileAppId) { context.getString(R.string.privacy_uid_changed) }
        }
        checkSafety(pkg, user, approved)
        val failures = mutableListOf<String>()
        suspend fun command(vararg args: String) {
            val result = shell.exec(args.toList())
            if (!result.success) failures += result.message
        }
        var skipped = 0
        when (action) {
            PrivacyAction.PERMISSIONS -> {
                val entries = before.getJSONArray("permissions")
                for (index in 0 until entries.length()) {
                    val permission = entries.getJSONObject(index)
                    if (!PrivacyPolicy.mutablePermission(permission.getInt("flags"))) { skipped++; continue }
                    val name = permission.getString("name")
                    command("pm", "revoke", "--user", "$user", pkg, name)
                    command("pm", "set-permission-flags", "--user", "$user", pkg, name, "user-fixed")
                }
            }
            PrivacyAction.BACKGROUND -> {
                check(!before.getBoolean("uidOverride")) { context.getString(R.string.privacy_uid_override) }
                command("cmd", "appops", "set", "--user", "$user", pkg, "RUN_ANY_IN_BACKGROUND", "ignore")
                if (before.getInt("standbyBucket") != 5) command("am", "set-standby-bucket", "--user", "$user", pkg, "restricted")
                else failures += context.getString(R.string.privacy_standby_exempt)
            }
            PrivacyAction.METERED -> {
                if (reconcileAppId == null) desired.setMetered(pkg, user, before.getInt("appId"), true)
                command("cmd", "netpolicy", "add", "restrict-background-blacklist", "${PrivacyPolicy.uid(user, before.getInt("appId"))}")
            }
            PrivacyAction.NETWORK -> {
                if (reconcileAppId == null) desired.setBlock(pkg, user, before.getInt("appId"), true)
                command("cana-privacy", "network-set", pkg, "$user", "${before.getInt("appId")}", "2")
            }
        }
        val after = snapshot(pkg, user, action)
        val applied = when (action) {
            PrivacyAction.PERMISSIONS -> after.getJSONArray("permissions").let { list ->
                (0 until list.length()).all { index -> list.getJSONObject(index).let {
                    !PrivacyPolicy.mutablePermission(it.getInt("flags")) || (!it.getBoolean("granted") && it.getInt("flags") and 2 != 0)
                } }
            }
            PrivacyAction.BACKGROUND -> after.getString("runAnyInBackground") == "ignore" && after.getInt("standbyBucket") == 45
            PrivacyAction.METERED -> after.getInt("meteredPolicy") and 1 != 0
            PrivacyAction.NETWORK -> after.getBoolean("chainEnabled") && after.getInt("networkRule") == 2 && after.getBoolean("desiredBlock")
        }
        if (!applied && failures.isEmpty()) failures += context.getString(R.string.operation_not_applied)
        OperationResult(failures.isEmpty(), failures.joinToString("\n").ifBlank {
            context.getString(R.string.privacy_applied, skipped)
        })
    }

    suspend fun undo(record: OperationRecord, batchId: String = UUID.randomUUID().toString()): OperationResult {
        val action = PrivacyAction.entries.find { it.key == record.action } ?: return OperationResult(false, context.getString(R.string.undo_unsupported))
        val records = history.records.first()
        if (record !in records) return OperationResult(false, context.getString(R.string.operation_history_failed))
        if (records.any { it.undoOf == record.id && it.completed && it.success }) return OperationResult(true, context.getString(R.string.operation_skipped), skipped = true)
        return journal.run(record.packageName, record.userId, action.key, batchId, record.id,
            snapshot = { snapshot(record.packageName, record.userId, action) }) { current ->
            val prior = JSONObject(record.previousState)
            check(prior.getInt("appId") == current.getInt("appId")) { context.getString(R.string.privacy_uid_changed) }
            val plan = RestoreScript.plan(record)
            check(plan.notes.isEmpty() && plan.commands.isNotEmpty()) { plan.notes.joinToString("\n") }
            if (action == PrivacyAction.NETWORK) desired.setBlock(record.packageName, record.userId,
                prior.getInt("appId"), prior.getBoolean("desiredBlock"))
            if (action == PrivacyAction.METERED) {
                // Old history predates persistent metered intent, so its previous intent was absent.
                prior.put("desiredMetered", prior.optBoolean("desiredMetered"))
                desired.setMetered(record.packageName, record.userId, prior.getInt("appId"), prior.getBoolean("desiredMetered"))
            }
            val results = plan.commands.filterNot { it.getOrNull(1) in setOf("desired-set", "metered-desired-set") }.map { shell.exec(it) }
            val after = snapshot(record.packageName, record.userId, action)
            // The shared deny chain remains enabled; removing our UID rule restores this app
            // without disabling blocks belonging to other apps or software.
            if (action == PrivacyAction.NETWORK) { prior.remove("chainEnabled"); after.remove("chainEnabled") }
            val success = results.all { it.success } && prior.toString() == after.toString()
            OperationResult(success, results.filterNot { it.success }.joinToString("\n") { it.message }.ifBlank {
                context.getString(if (success) R.string.operation_success else R.string.operation_not_applied)
            })
        }
    }

    suspend fun reapplyDesired(): BatchResult {
        val results = mutableListOf<OperationResult>()
        val batch = UUID.randomUUID().toString()
        val entries = desired.blocks.first().map { it to PrivacyAction.NETWORK } + desired.meteredBlocks.first().map { it to PrivacyAction.METERED }
        for ((block, action) in entries) {
            try {
                val live = snapshot(block.packageName, block.userId, action)
                if (live.getInt("appId") != block.appId) {
                    results += OperationResult(false, context.getString(R.string.privacy_uid_changed)); continue
                }
                val missing = if (action == PrivacyAction.METERED) live.getInt("meteredPolicy") and 1 == 0
                    else live.getInt("networkRule") != 2 || !live.getBoolean("chainEnabled")
                if (missing) {
                    // Roles/admins may have changed since the original consent. Require fresh consent.
                    results += restrict(block.packageName, block.userId, action, batchId = batch, reconcileAppId = block.appId)
                }
            } catch (e: Exception) {
                LogUtils.e("PrivacyOps", "Cannot reconcile ${block.packageName} user=${block.userId}", e)
                results += OperationResult(false, (e.cause ?: e).message.orEmpty())
            }
        }
        return BatchResult(results, batchId = batch)
    }

    suspend fun restoreDesired(pkg: String, user: Int, appId: Int, blocked: Boolean, action: PrivacyAction = PrivacyAction.NETWORK): OperationResult =
        journal.run(pkg, user, "${action.key}_desired_restore", UUID.randomUUID().toString(), snapshot = {
            val entries = if (action == PrivacyAction.METERED) desired.meteredBlocks.first() else desired.blocks.first()
            JSONObject().put("appId", appId).put(if (action == PrivacyAction.METERED) "desiredMetered" else "desiredBlock", entries.any { it.packageName == pkg && it.userId == user })
        }) {
            if (action == PrivacyAction.METERED) desired.setMetered(pkg, user, appId, blocked)
            else desired.setBlock(pkg, user, appId, blocked)
            OperationResult(true, context.getString(R.string.operation_success))
        }
}
