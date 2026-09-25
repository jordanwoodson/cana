package io.github.samolego.canta.ops

import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.LockdownSettings
import kotlinx.coroutines.flow.first
import java.util.UUID

class PresetOps(private val packages: PackageOps, private val privacy: PrivacyOps, private val history: HistoryStore) {
    suspend fun captureLockdown(user: Int): List<LockdownSettings> {
        val records = history.records.first()
        val candidates = records.filter { it.userId == user && it.action in PrivacyAction.entries.map { action -> action.key } && it.changed }
            .map { it.packageName }.distinct()
        return candidates.mapNotNull { pkg ->
            val app = packages.getPackageInfo(pkg, user)?.applicationInfo
            if (app == null || app.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED == 0) return@mapNotNull null
            val live = privacy.load(pkg, user)
            val usedActions = records.filter { it.packageName == pkg && it.userId == user && it.changed }.map { it.action }.toSet()
            live.errors.entries.firstOrNull { it.key.key in usedActions }?.let { error("$pkg: ${it.value}") }
            val permissions = live.values[PrivacyAction.PERMISSIONS]?.getJSONArray("permissions")
            val revoked = permissions != null && permissions.length() > 0 && (0 until permissions.length()).all {
                permissions.getJSONObject(it).let { permission -> !PrivacyPolicy.mutablePermission(permission.getInt("flags")) ||
                    !permission.getBoolean("granted") && permission.getInt("flags") and 2 != 0 }
            }
            val background = live.values[PrivacyAction.BACKGROUND]?.getString("runAnyInBackground") == "ignore"
            val metered = (live.values[PrivacyAction.METERED]?.getInt("meteredPolicy") ?: 0) and 1 != 0
            if (!revoked && !background && !metered && !live.desiredBlock) null
            else LockdownSettings(pkg, revoked, background, metered, live.desiredBlock)
        }
    }

    suspend fun apply(preset: CantaPresetData, users: List<Int>, approved: Map<Int, Set<String>>): BatchResult {
        val batch = UUID.randomUUID().toString()
        val results = mutableListOf<OperationResult>()
        for (user in users.distinct()) {
            for (pkg in preset.apps) {
                val result = packages.uninstall(pkg, user, batchId = batch, approvedWarnings = approved[user].orEmpty())
                results += result.copy(message = "$pkg · user $user: ${result.message}")
            }
            for (entry in preset.lockdown) for (action in entry.actions()) {
                val result = privacy.restrict(entry.packageName, user, action, approved[user].orEmpty(), batch)
                results += result.copy(message = "${entry.packageName} · user $user · ${action.key}: ${result.message}")
            }
        }
        return BatchResult(results, batchId = batch)
    }
}

fun LockdownSettings.actions(): List<PrivacyAction> = buildList {
    if (revokePermissions) add(PrivacyAction.PERMISSIONS)
    if (restrictBackground) add(PrivacyAction.BACKGROUND)
    if (denyMetered) add(PrivacyAction.METERED)
    if (blockNetwork) add(PrivacyAction.NETWORK)
}
