package io.github.samolego.canta.ops

import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.LockdownSettings
import kotlinx.coroutines.flow.first
import java.util.UUID

class PresetOps(private val packages: PackageOps, private val privacy: PrivacyOps, private val history: HistoryStore,
    private val batches: BatchCoordinator? = null) {
    suspend fun captureLockdown(user: Int): List<LockdownSettings> {
        val records = history.allRecords()
        val candidates = records.filter { it.userId == user && it.action in PrivacyAction.entries.map { action -> action.key } && it.changed }
            .map { it.packageName }.distinct()
        return candidates.mapNotNull { pkg ->
            val app = packages.getPackageInfo(pkg, user)?.applicationInfo
            if (app == null || app.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED == 0) return@mapNotNull null
            val live = privacy.load(pkg, user)
            val usedActions = records.filter { it.packageName == pkg && it.userId == user && it.changed }.map { it.action }.toSet()
            live.errors.entries.firstOrNull { it.key.key in usedActions }?.let { error("$pkg: ${it.value}") }
            PresetLockdownCapture.capture(pkg, live)
        }
    }

    suspend fun apply(preset: CantaPresetData, users: List<Int>, approved: Map<Int, Set<String>>): BatchResult {
        val captured = preset.copy(apps = preset.apps.toSet(), lockdown = preset.lockdown.toList())
        val approvals = approved.mapValues { it.value.toSet() }
        val items = buildList {
            for (user in users.distinct()) {
                for (pkg in captured.apps.sorted()) add(BatchItem(size.toString(), pkg, user, "uninstall"))
                for (entry in captured.lockdown) for (action in entry.actions())
                    add(BatchItem(size.toString(), entry.packageName, user, action.key))
            }
        }
        val execute: suspend (BatchItem, String) -> OperationResult = { item, batch ->
            val result = if (item.action == "uninstall") packages.uninstall(item.packageName, item.userId,
                batchId = batch, approvedWarnings = approvals[item.userId].orEmpty())
            else privacy.restrict(item.packageName, item.userId, PrivacyAction.entries.single { it.key == item.action },
                approvals[item.userId].orEmpty(), batch)
            result.copy(message = "${item.packageName} · user ${item.userId}: ${result.message}")
        }
        return if (batches != null) batches.run(captured.name, items, execute)
        else {
            val id = UUID.randomUUID().toString()
            BatchResult(items.map { execute(it, id) }, id)
        }
    }
}

fun LockdownSettings.actions(): List<PrivacyAction> = buildList {
    if (revokePermissions) add(PrivacyAction.PERMISSIONS)
    if (restrictBackground) add(PrivacyAction.BACKGROUND)
    if (denyMetered) add(PrivacyAction.METERED)
    if (blockNetwork) add(PrivacyAction.NETWORK)
}

internal object PresetLockdownCapture {
    fun capture(pkg: String, live: PrivacyState): LockdownSettings? {
        val permissions = live.values[PrivacyAction.PERMISSIONS]?.getJSONArray("permissions")
        val mutable = permissions?.let { list -> (0 until list.length()).map { list.getJSONObject(it) }
            .filter { PrivacyPolicy.mutablePermission(it.getInt("flags")) } }.orEmpty()
        val revoked = mutable.isNotEmpty() && mutable.all { !it.getBoolean("granted") && it.getInt("flags") and 2 != 0 }
        val background = live.values[PrivacyAction.BACKGROUND]?.getString("runAnyInBackground") == "ignore"
        val metered = live.desiredMetered || (live.values[PrivacyAction.METERED]?.getInt("meteredPolicy") ?: 0) and 1 != 0
        return if (!revoked && !background && !metered && !live.desiredBlock) null
        else LockdownSettings(pkg, revoked, background, metered, live.desiredBlock)
    }
}
