package io.github.samolego.canta.ops

import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.data.proto.OperationRecord
import kotlinx.coroutines.flow.first
import java.util.UUID

class UndoCoordinator(private val history: HistoryStore, private val packages: PackageOps, private val privacy: PrivacyOps) {
    suspend fun lastBatch(): List<OperationRecord> = ManagementPolicy.lastBatch(history.records.first())
    suspend fun undo(records: List<OperationRecord>, approved: Map<Int, Set<String>> = emptyMap()): BatchResult {
        val batch = UUID.randomUUID().toString()
        val results = records.map { record ->
            val result = if (record.action in PrivacyAction.entries.map { it.key }) privacy.undo(record, batch)
                else packages.undo(record, batch, approved[record.userId].orEmpty())
            result.copy(message = "${record.packageName} · user ${record.userId} · ${record.action}: ${result.message}")
        }
        return BatchResult(results, batch)
    }
}
