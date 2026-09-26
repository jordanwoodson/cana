package io.github.samolego.canta.ops

import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.data.proto.PlannedBatch
import org.junit.Assert.*
import org.junit.Test

class HistoryTimelineTest {
    @Test fun groupsActualChangesWithPlannedButUnstartedItems() {
        val plan = PlannedBatch.newBuilder().setId("batch").setCreatedMs(2).build()
        val record = OperationRecord.newBuilder().setId("one").setBatchId("batch").build()
        val emptyPlan = PlannedBatch.newBuilder().setId("empty").setCreatedMs(3).build()
        val result = HistoryTimeline.group(listOf(record), listOf(plan, emptyPlan))
        assertEquals(listOf("empty", "batch"), result.map { it.id })
        assertEquals(listOf(record), result.last().records)
        assertTrue(result.first().records.isEmpty())
    }
    @Test fun legacyRecordsWithoutBatchIdRemainSeparate() {
        val records = listOf("one", "two").map { OperationRecord.newBuilder().setId(it).build() }
        assertEquals(2, HistoryTimeline.group(records, emptyList()).size)
    }
}
