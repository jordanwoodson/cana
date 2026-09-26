package io.github.samolego.canta.ops

import io.github.samolego.canta.data.proto.OperationRecord
import org.json.JSONObject

data class PackageInventory(val userId: Int, val installed: Set<String>, val system: Set<String>)
data class OtaChange(val userId: Int, val packageName: String, val returned: Boolean)

object ManagementPolicy {
    private val undoable = setOf("uninstall", "uninstall_keep_data", "reinstall", "remove_updates", "disable", "enable",
        "suspend", "unsuspend", "component", "permissions", "background", "metered", "network", "system")
    fun lastBatch(records: List<OperationRecord>): List<OperationRecord> {
        val undone = records.filter { it.undoOf.isNotEmpty() && it.completed && (it.success || it.recoveryComplete) }.map { it.undoOf }.toSet()
        val candidates = records.filter { it.undoOf.isEmpty() && it.id !in undone && it.action in undoable &&
            (it.changed || !it.completed && it.previousState.isNotBlank() && it.previousState != "{}") }
        val batch = candidates.lastOrNull()?.batchId ?: return emptyList()
        return candidates.filter { it.batchId == batch }.asReversed()
    }
    fun otaChanges(previousFingerprint: String, fingerprint: String, previous: List<PackageInventory>,
        current: List<PackageInventory>, records: List<OperationRecord>): List<OtaChange> {
        if (previousFingerprint.isBlank() || previousFingerprint == fingerprint) return emptyList()
        val removals = mutableMapOf<Pair<Int, String>, Boolean>()
        for (record in records) {
            if (record.action !in setOf("uninstall", "uninstall_keep_data", "reinstall", "remove_updates") || !record.completed) continue
            val after = runCatching { JSONObject(record.afterState) }.getOrNull() ?: continue
            if (after.has("installed") && (record.changed || after.getBoolean("installed")))
                removals[record.userId to record.packageName] = !after.getBoolean("installed")
        }
        return current.flatMap { inventory ->
            val before = previous.find { it.userId == inventory.userId } ?: return@flatMap emptyList()
            val returned = inventory.installed.filter { removals[inventory.userId to it] == true }.toSet()
            returned.map { OtaChange(inventory.userId, it, true) } +
                (inventory.system - before.system - returned).map { OtaChange(inventory.userId, it, false) }
        }
    }
    fun unused90Days(lastUsed: Long?, installedAt: Long, now: Long): Boolean =
        lastUsed != null && installedAt > 0 && now - maxOf(lastUsed, installedAt) >= 90L * 86_400_000L
}
