package io.github.samolego.canta.ops

import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.data.proto.PlannedBatch

data class HistoryGroup(val id: String, val plan: PlannedBatch?, val records: List<OperationRecord>) {
    val timestamp: Long get() = plan?.createdMs ?: records.maxOfOrNull { it.timestampMs } ?: 0
}

object HistoryTimeline {
    fun group(records: List<OperationRecord>, batches: List<PlannedBatch>): List<HistoryGroup> {
        val entries = records.groupBy { it.batchId.ifBlank { it.id } }
        val plans = batches.associateBy { it.id }
        return (entries.keys + plans.keys).map { id -> HistoryGroup(id, plans[id], entries[id].orEmpty()) }
            .sortedByDescending { it.timestamp }
    }
}
