package io.github.samolego.canta.ops

import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.util.PackageInstallerResult

/** Read-only settlement. A live request is never settled just because an interim state looks final. */
internal object PendingPackageRecovery {
    fun finish(record: OperationRecord, after: String, settled: Boolean, message: String, original: OperationRecord? = null): OperationResult {
        val notes = if (settled && record.undoOf.isNotBlank() && original != null) RestoreScript.plan(original).notes else emptyList()
        return OperationResult(settled && notes.isEmpty(), notes.joinToString("\n").ifBlank { message },
            record.previousState != "{}" && !SnapshotState.equal(record.previousState, after),
            recoveryComplete = settled && record.undoOf.isNotBlank())
    }

    fun settle(record: OperationRecord, after: String, terminal: PackageInstallerResult.Result?, awaiting: Boolean): Boolean? {
        if (awaiting) return null
        val before = runCatching { SnapshotState.read(record.previousState) }.getOrNull() ?: return null
        // Empty pre-state means preflight failed before the mutation closure could run.
        if (before.length() == 0) return false
        val state = runCatching { SnapshotState.read(after) }.getOrNull() ?: return null
        if (record.action in setOf("enable", "disable", "suspend", "unsuspend", "component") &&
            before.optBoolean("installed") && !state.optBoolean("installed")) {
            return if (terminal != null) false else null
        }
        val expected = if (record.intendedState.isNotBlank()) {
            val goal = runCatching { SnapshotState.read(record.intendedState) }.getOrNull() ?: return null
            val keys = goal.keys().asSequence().filter { it != "snapshotSchema" }.toList()
            if (keys.isEmpty()) return null
            keys.all { state.has(it) } && SnapshotState.equal(goal, org.json.JSONObject().apply {
                keys.forEach { put(it, state.get(it)) }
            })
        } else {
            if (record.undoOf.isNotEmpty()) return null
            when (record.action) {
            "uninstall", "uninstall_keep_data" -> state.has("installed") && !state.getBoolean("installed")
            "reinstall" -> state.optBoolean("installed")
            "remove_updates" -> state.has("installed") && state.has("updatedSystemApp") &&
                !state.getBoolean("installed") && !state.getBoolean("updatedSystemApp")
            "disable" -> state.optInt("enabledSetting", -1) == 3
            "enable" -> state.optInt("enabledSetting", -1) == 1
            "suspend" -> state.optBoolean("suspended")
            "unsuspend" -> state.has("suspended") && !state.getBoolean("suspended")
            else -> return null
            }
        }
        return when {
            terminal != null -> terminal.success && expected
            expected -> true
            else -> null // A process restart lost the callback; absence of change proves nothing.
        }
    }
}
