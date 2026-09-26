package io.github.samolego.canta.ops

import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.data.proto.OperationRecord
import kotlinx.coroutines.flow.first
import java.util.UUID

class UndoCoordinator(private val history: HistoryStore, private val packages: PackageOps, private val privacy: PrivacyOps, private val system: SystemOps,
    private val batches: BatchCoordinator? = null) {
    suspend fun lastBatch(): List<OperationRecord> = ManagementPolicy.lastBatch(history.records.first())
    suspend fun undo(records: List<OperationRecord>, approved: Map<Int, Set<String>> = emptyMap()): BatchResult {
        val captured = records.toList()
        val approvals = approved.mapValues { it.value.toSet() }
        val execute: suspend (OperationRecord, String) -> OperationResult = { record, batch ->
            val result = if (record.action == "system") system.undo(record, batch)
                else if (record.action in PrivacyAction.entries.map { it.key }) privacy.undo(record, batch, approvals[record.userId].orEmpty())
                else packages.undo(record, batch, approvals[record.userId].orEmpty())
            result.copy(message = "${record.packageName} · user ${record.userId}: ${result.message}")
        }
        return if (batches != null) batches.run("Undo", captured.mapIndexed { index, record ->
            BatchItem(index.toString(), record.packageName, record.userId, "undo_${record.action}")
        }) { item, batch -> execute(captured[item.key.toInt()], batch) }
        else {
            val batch = UUID.randomUUID().toString()
            BatchResult(captured.map { execute(it, batch) }, batch)
        }
    }
}
