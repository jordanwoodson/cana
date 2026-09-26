package io.github.samolego.canta.ops

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import io.github.samolego.canta.R
import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.data.PrivacyStore
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.data.proto.NetworkBlock
import kotlinx.coroutines.CancellationException
import rikka.shizuku.Shizuku
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils as Packages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.Base64

enum class PrivacyAction(val key: String, val title: Int) {
    PERMISSIONS("permissions", R.string.privacy_permissions), BACKGROUND("background", R.string.privacy_background),
    METERED("metered", R.string.privacy_metered), NETWORK("network", R.string.privacy_network);
}
data class PrivacyState(val values: Map<PrivacyAction, JSONObject>, val errors: Map<PrivacyAction, String>,
    val sharedPackages: List<String>, val desiredBlock: Boolean, val desiredMetered: Boolean = false,
    val connected: Boolean = true, val saved: Map<PrivacyAction, NetworkBlock> = emptyMap())

class PrivacyOps(private val context: Context, private val shell: ShellRunner,
    private val history: HistoryStore, private val desired: PrivacyStore,
    private val safety: SafetyInspector, private val journal: OperationJournal,
) {
    private val ownerUserId = android.os.Process.myUid() / 100_000
    suspend fun initialize() = desired.bindInstallation(OperationalIdentity.current(context))
    private fun owned(args: List<String>) = if (args.firstOrNull() == "cana-privacy")
        listOf("cana-privacy", "--owner-user", "$ownerUserId") + args.drop(1) else args
    private suspend fun exec(vararg args: String): String {
        val result = shell.exec(owned(args.toList()))
        check(result.success) { result.message }
        return result.stdout.trim()
    }
    private class NeedsReview(message: String) : IllegalStateException(message)
    private fun info(pkg: String, user: Int): PackageInfo =
        Packages.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS, user)
            ?: throw NeedsReview(context.getString(R.string.operation_package_missing))
    private fun shared(pkg: String, user: Int): List<String> {
        val appId = info(pkg, user).applicationInfo!!.uid % 100_000
        PrivacyPolicy.uid(user, appId)
        return Packages.getInstalledPackages(0, user).filter { it.applicationInfo!!.uid % 100_000 == appId }
            .map { it.packageName }.sorted()
    }
    suspend fun assess(pkg: String, user: Int): Map<String, SafetyAssessment> = withContext(Dispatchers.IO) {
        safety.inspect(shared(pkg, user), user)
    }
    private data class Evidence(val identity: String, val conditions: String, val approvals: Set<String>)
    private suspend fun identity(pkg: String, user: Int): String =
        JSONObject(exec("cana-privacy", "identity-get", pkg, "$user", "-")).getString("identity")
    private suspend fun evidence(pkg: String, user: Int, approved: Set<String>): Evidence {
        val peers = shared(pkg, user)
        val reports = safety.inspect(peers, user)
        val required = approvalKeys(pkg, user, reports)
        reports.forEach { (name, report) ->
            check(report.error == null) { report.error.orEmpty() }
            if (report.protected) throw NeedsReview(context.getString(R.string.safety_protected, name))
        }
        if (!approved.containsAll(required)) throw NeedsReview(context.getString(R.string.safety_confirmation_required, pkg))
        val identities = peers.sorted().associateWith { identity(it, user) }
        return Evidence(identities.getValue(pkg), JSONObject().put("peers", JSONObject(identities))
            .put("warnings", JSONArray(required.sorted())).toString(), required)
    }
    companion object {
        private fun captureConsent(block: NetworkBlock?): String = block?.toBuilder()?.clearStatus()?.clearStatusMessage()?.clearCheckedAtMs()?.build()
            ?.toByteArray()?.let { Base64.getEncoder().encodeToString(it) }.orEmpty()
        private fun readConsent(encoded: String?): NetworkBlock? = encoded?.takeIf { it.isNotEmpty() }
            ?.let { NetworkBlock.parseFrom(Base64.getDecoder().decode(it)) }
        fun approvalKeys(pkg: String, user: Int, reports: Map<String, SafetyAssessment>): Set<String> =
            reports.values.flatMap { it.warnings }.map { it.key }.toSet() +
                if (reports.size > 1) setOf("shared-uid:$user:$pkg:${reports.keys.sorted().joinToString(",")}") else emptySet()
    }
    suspend fun load(pkg: String, user: Int): PrivacyState = withContext(Dispatchers.IO) {
        initialize()
        val saved = buildMap {
            desired.blocks.first().firstOrNull { it.packageName == pkg && it.userId == user }?.let { put(PrivacyAction.NETWORK, it) }
            desired.meteredBlocks.first().firstOrNull { it.packageName == pkg && it.userId == user }?.let { put(PrivacyAction.METERED, it) }
        }
        val connected = Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        val values = mutableMapOf<PrivacyAction, JSONObject>()
        val errors = mutableMapOf<PrivacyAction, String>()
        if (connected) for (action in PrivacyAction.entries) {
            try { values[action] = snapshot(pkg, user, action) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { errors[action] = (e.cause ?: e).message.orEmpty() }
        }
        PrivacyState(values, errors, if (connected) runCatching { shared(pkg, user) }.getOrDefault(emptyList()) else emptyList(),
            PrivacyAction.NETWORK in saved, PrivacyAction.METERED in saved, connected, saved)
    }
    suspend fun snapshot(pkg: String, user: Int, action: PrivacyAction): JSONObject = withContext(Dispatchers.IO) {
        val info = info(pkg, user)
        val appId = info.applicationInfo!!.uid % 100_000
        val uid = PrivacyPolicy.uid(user, appId)
        val state = JSONObject().put("appId", appId).put("ownerUserId", ownerUserId).put("packageInstalledAt", info.firstInstallTime)
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
                val saved = desired.meteredBlocks.first().firstOrNull { it.packageName == pkg && it.userId == user }
                state.put("meteredPolicy", (if (denied) 1 else 0) or (if (allowed) 4 else 0))
                    .put("desiredMetered", saved != null).put("savedConsent", captureConsent(saved))
            }
            PrivacyAction.NETWORK -> {
                val live = JSONObject(exec("cana-privacy", "network-get", pkg, "$user", "$appId"))
                state.put("networkRule", live.getInt("networkRule")).put("chainEnabled", live.getBoolean("chainEnabled"))
                val saved = desired.blocks.first().firstOrNull { it.packageName == pkg && it.userId == user }
                state.put("desiredBlock", saved != null).put("savedConsent", captureConsent(saved))
            }
        }
        state
    }

    suspend fun restrict(pkg: String, user: Int, action: PrivacyAction, approved: Set<String> = emptySet(),
        batchId: String = UUID.randomUUID().toString(),
        reconcileAppId: Int? = null,
        selectedPermissions: Set<String>? = null,
    ): OperationResult {
        initialize()
        val result = journal.run(pkg, user, if (reconcileAppId == null) action.key else "${action.key}_reapply", batchId,
        snapshot = { snapshot(pkg, user, action).let { if (action == PrivacyAction.PERMISSIONS) PermissionChoices.snapshot(it, selectedPermissions) else it } }) { before ->
        if (reconcileAppId != null) {
            if (!before.optBoolean(if (action == PrivacyAction.METERED) "desiredMetered" else "desiredBlock")) return@run OperationResult(true, context.getString(R.string.operation_skipped), skipped = true)
            check(before.getInt("appId") == reconcileAppId) { context.getString(R.string.privacy_uid_changed) }
        }
        val consent = evidence(pkg, user, approved)
        if (reconcileAppId != null) {
            val entries = if (action == PrivacyAction.METERED) desired.meteredBlocks.first() else desired.blocks.first()
            val saved = entries.firstOrNull { it.packageName == pkg && it.userId == user }
                ?: return@run OperationResult(true, context.getString(R.string.operation_skipped), skipped = true)
            check(PrivacyReviewPolicy.reusable(saved.identity, consent.identity, saved.safetyConditions, consent.conditions)) {
                context.getString(R.string.privacy_review_required)
            }
        }
        suspend fun saveIntent() {
            if (reconcileAppId == null) desired.saveReviewed(NetworkBlock.newBuilder().setPackageName(pkg).setUserId(user)
                .setAppId(before.getInt("appId")).setIdentity(consent.identity).setSafetyConditions(consent.conditions)
                .addAllApprovals(consent.approvals).setStatus("pending").build(), action == PrivacyAction.METERED)
        }
        val failures = mutableListOf<String>()
        suspend fun command(vararg args: String) {
            val result = shell.exec(owned(args.toList()))
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
                saveIntent()
                command("cmd", "netpolicy", "add", "restrict-background-blacklist", "${PrivacyPolicy.uid(user, before.getInt("appId"))}")
            }
            PrivacyAction.NETWORK -> {
                saveIntent()
                command("cana-privacy", "network-set", pkg, "$user", "${before.getInt("appId")}", "2")
            }
        }
        val after = snapshot(pkg, user, action).let { if (action == PrivacyAction.PERMISSIONS) PermissionChoices.snapshot(it, selectedPermissions) else it }
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
        if (action in setOf(PrivacyAction.NETWORK, PrivacyAction.METERED)) desired.status(pkg, user,
            action == PrivacyAction.METERED, if (result.success) "verified" else "failed", if (result.success) "" else result.message)
        return result
    }

    suspend fun undo(record: OperationRecord, batchId: String = UUID.randomUUID().toString(), approved: Set<String> = emptySet()): OperationResult {
        initialize()
        val action = PrivacyAction.entries.find { it.key == record.action } ?: return OperationResult(false, context.getString(R.string.undo_unsupported))
        val records = history.records.first()
        if (record !in records) return OperationResult(false, context.getString(R.string.operation_history_failed))
        if (records.any { it.undoOf == record.id && it.completed && it.success }) return OperationResult(true, context.getString(R.string.operation_skipped), skipped = true)
        val choices = PermissionChoices.selected(SnapshotState.read(record.previousState))
        return journal.run(record.packageName, record.userId, action.key, batchId, record.id,
            snapshot = { snapshot(record.packageName, record.userId, action).let {
                if (action == PrivacyAction.PERMISSIONS) PermissionChoices.snapshot(it, choices) else it
            } }) { current ->
            val prior = SnapshotState.read(record.previousState)
            check(prior.getInt("appId") == current.getInt("appId") &&
                (!prior.has("packageInstalledAt") || prior.getLong("packageInstalledAt") == current.getLong("packageInstalledAt"))) {
                context.getString(R.string.privacy_uid_changed)
            }
            if (PrivacyReviewPolicy.undoNeedsConsent(action.key, prior, current)) evidence(record.packageName, record.userId, approved)
            val plan = RestoreScript.plan(record)
            check(plan.notes.isEmpty() && plan.commands.isNotEmpty()) { plan.notes.joinToString("\n") }
            if (action == PrivacyAction.NETWORK) desired.restoreIntent(record.packageName, record.userId,
                prior.getInt("appId"), prior.getBoolean("desiredBlock"), false, readConsent(prior.optString("savedConsent")))
            if (action == PrivacyAction.METERED) {
                // Old history predates persistent metered intent, so its previous intent was absent.
                prior.put("desiredMetered", prior.optBoolean("desiredMetered"))
                desired.restoreIntent(record.packageName, record.userId, prior.getInt("appId"), prior.getBoolean("desiredMetered"), true,
                    readConsent(prior.optString("savedConsent")))
            }
            val results = plan.commands.filterNot { it.getOrNull(1) in setOf("desired-set", "metered-desired-set") }.map { shell.exec(owned(it)) }
            val after = snapshot(record.packageName, record.userId, action).let {
                if (action == PrivacyAction.PERMISSIONS) PermissionChoices.snapshot(it, choices) else it
            }
            if (!prior.has("ownerUserId")) after.remove("ownerUserId")
            if (!prior.has("packageInstalledAt")) after.remove("packageInstalledAt")
            if (!prior.has("savedConsent")) after.remove("savedConsent")
            // The shared deny chain remains enabled; removing our UID rule restores this app
            // without disabling blocks belonging to other apps or software.
            if (action == PrivacyAction.NETWORK) { prior.remove("chainEnabled"); after.remove("chainEnabled") }
            val success = results.all { it.success } && SnapshotState.equal(prior, after)
            OperationResult(success, results.filterNot { it.success }.joinToString("\n") { it.message }.ifBlank {
                context.getString(if (success) R.string.operation_success else R.string.operation_not_applied)
            })
        }
    }

    suspend fun markDisconnected() {
        initialize()
        for ((block, action) in savedEntries()) if (block.status != "needs_review")
            desired.status(block.packageName, block.userId, action == PrivacyAction.METERED, "disconnected")
    }
    private suspend fun savedEntries() = desired.blocks.first().map { it to PrivacyAction.NETWORK } +
        desired.meteredBlocks.first().map { it to PrivacyAction.METERED }

    /** Forget only Cana's saved intent. Never resolve or mutate an old/recycled live UID. */
    suspend fun forgetDesired(pkg: String, user: Int, action: PrivacyAction): OperationResult {
        require(action in setOf(PrivacyAction.NETWORK, PrivacyAction.METERED))
        initialize()
        return journal.run(pkg, user, "${action.key}_forget", UUID.randomUUID().toString(), snapshot = {
            JSONObject().put("saved", savedEntries().any { it.first.packageName == pkg && it.first.userId == user && it.second == action })
        }) {
            desired.forget(pkg, user, action == PrivacyAction.METERED)
            OperationResult(true, context.getString(R.string.privacy_forgotten))
        }
    }

    suspend fun reapplyDesired(): BatchResult {
        initialize()
        val results = mutableListOf<OperationResult>()
        val batch = UUID.randomUUID().toString()
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            markDisconnected()
            return BatchResult(savedEntries().map { OperationResult(false, context.getString(R.string.privacy_disconnected_body)) }, batch)
        }
        for ((block, action) in savedEntries()) {
            val metered = action == PrivacyAction.METERED
            try {
                if (block.identity.isEmpty()) {
                    desired.status(block.packageName, block.userId, metered, "needs_review")
                    results += OperationResult(false, context.getString(R.string.privacy_review_required)); continue
                }
                val consent = try { evidence(block.packageName, block.userId, block.approvalsList.toSet()) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        desired.status(block.packageName, block.userId, metered,
                            if (e is NeedsReview) "needs_review" else "failed", e.message.orEmpty())
                        results += OperationResult(false, e.message.orEmpty()); continue
                    }
                if (!PrivacyReviewPolicy.reusable(block.identity, consent.identity, block.safetyConditions, consent.conditions)) {
                    desired.status(block.packageName, block.userId, metered, "needs_review")
                    results += OperationResult(false, context.getString(R.string.privacy_review_required)); continue
                }
                val live = snapshot(block.packageName, block.userId, action)
                val missing = if (metered) live.getInt("meteredPolicy") and 1 == 0
                    else live.getInt("networkRule") != 2 || !live.getBoolean("chainEnabled")
                if (missing) results += restrict(block.packageName, block.userId, action, block.approvalsList.toSet(),
                    batchId = batch, reconcileAppId = block.appId)
                else desired.status(block.packageName, block.userId, metered, "verified")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                LogUtils.e("PrivacyOps", "Cannot reconcile ${block.packageName} user=${block.userId}", e)
                desired.status(block.packageName, block.userId, metered, "failed", (e.cause ?: e).message.orEmpty())
                results += OperationResult(false, (e.cause ?: e).message.orEmpty())
            }
        }
        return BatchResult(results, batchId = batch)
    }

    suspend fun restoreDesired(pkg: String, user: Int, appId: Int, blocked: Boolean,
        action: PrivacyAction = PrivacyAction.NETWORK, savedConsent: String? = null): OperationResult {
        require(action in setOf(PrivacyAction.NETWORK, PrivacyAction.METERED))
        initialize()
        return journal.run(pkg, user, "${action.key}_desired_restore", UUID.randomUUID().toString(), snapshot = {
            val entries = if (action == PrivacyAction.METERED) desired.meteredBlocks.first() else desired.blocks.first()
            val saved = entries.firstOrNull { it.packageName == pkg && it.userId == user }
            JSONObject().put("appId", appId).put("ownerUserId", ownerUserId).put("savedConsent", captureConsent(saved))
                .put(if (action == PrivacyAction.METERED) "desiredMetered" else "desiredBlock", saved != null)
        }) {
            desired.restoreIntent(pkg, user, appId, blocked, action == PrivacyAction.METERED, readConsent(savedConsent))
            OperationResult(true, context.getString(R.string.operation_success))
        }
    }
}
