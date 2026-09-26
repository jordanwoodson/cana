package io.github.samolego.canta.ops

import androidx.datastore.core.DataStoreFactory
import io.github.samolego.canta.data.BatchSerializer
import io.github.samolego.canta.data.BatchStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BatchCoordinatorTest {
    @get:Rule val folder = TemporaryFolder()
    private fun store(scope: CoroutineScope) = BatchStore(DataStoreFactory.create(BatchSerializer, scope = scope) {
        folder.newFolder().resolve("batches.pb")
    })

    @Test fun unknownOutcomeRemainsPendingUntilVerifiedReconciliation() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = store(owner)
            val result = BatchCoordinator(store, owner).run("Uninstall", listOf(BatchItem("a", "example.app", 10, "uninstall"))) { _, _ ->
                OperationResult(false, "Waiting for Android", outcomeUnknown = true)
            }
            assertEquals(1, result.unknownCount)
            assertEquals(0, result.failureCount)
            assertEquals("pending", store.batches.first().single().itemsList.single().status)
        } finally { owner.cancel() }
    }

    @Test fun leavingCallerDoesNotTruncateBatch() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = store(owner)
            val coordinator = BatchCoordinator(store, owner)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val executed = mutableListOf<String>()
            val caller = launch {
                coordinator.run("Disable", listOf(BatchItem("one", "a", 10, "disable"), BatchItem("two", "b", 10, "disable"))) { item, _ ->
                    if (item.key == "one") { entered.complete(Unit); release.await() }
                    executed += item.packageName
                    OperationResult(true, "Applied", changed = true)
                }
            }
            withTimeout(10_000) { entered.await() }
            caller.cancelAndJoin()
            release.complete(Unit)
            val finished = withTimeout(10_000) { store.batches.first { it.singleOrNull()?.completed == true }.single() }
            assertEquals(listOf("a", "b"), executed)
            assertTrue(finished.itemsList.all { it.status == "completed" && it.success })
        } finally { owner.cancel() }
    }

    @Test fun stopFinishesCurrentItemAndRecordsUnstartedItems() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = store(owner); val coordinator = BatchCoordinator(store, owner)
            val entered = CompletableDeferred<String>(); val release = CompletableDeferred<Unit>()
            val running = async {
                coordinator.run("Disable", listOf(BatchItem("one", "a", 0, "disable"), BatchItem("two", "b", 0, "disable"))) { _, id ->
                    entered.complete(id); release.await(); OperationResult(true, "Applied")
                }
            }
            val id = withTimeout(10_000) { entered.await() }
            coordinator.requestStop(id); release.complete(Unit)
            val result = withTimeout(10_000) { running.await() }
            assertEquals(1, result.successCount)
            assertEquals(1, result.skippedCount)
            assertEquals(listOf("completed", "cancelled"), store.batches.first().single().itemsList.map { it.status })
        } finally { owner.cancel() }
    }

    @Test fun restartMarksUnfinishedIntentForReviewWithoutReplay() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = store(owner)
            store.append("old", "Uninstall", listOf(BatchItem("one", "a", 10, "uninstall")))
            store.started("old", "one")
            val coordinator = BatchCoordinator(store, owner)
            coordinator.awaitReady()
            val record = store.batches.first().single()
            assertTrue(record.interrupted)
            assertEquals("interrupted", record.itemsList.single().status)
            assertNull(coordinator.active.value)
        } finally { owner.cancel() }
    }
    @Test fun storageFailureAfterMutationStopsFurtherItemsAndReportsReview() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val state = kotlinx.coroutines.flow.MutableStateFlow(io.github.samolego.canta.data.proto.PlannedBatches.getDefaultInstance())
            val data = object : androidx.datastore.core.DataStore<io.github.samolego.canta.data.proto.PlannedBatches> {
                override val data = state
                override suspend fun updateData(transform: suspend (io.github.samolego.canta.data.proto.PlannedBatches) -> io.github.samolego.canta.data.proto.PlannedBatches): io.github.samolego.canta.data.proto.PlannedBatches {
                    val next = transform(state.value)
                    if (next.batchesList.any { batch -> batch.itemsList.any { it.status == "completed" } }) throw java.io.IOException("Disk full")
                    state.value = next
                    return next
                }
            }
            val coordinator = BatchCoordinator(BatchStore(data), owner)
            var executed = 0
            val result = coordinator.run("Disable", listOf(BatchItem("a", "a", 0, "disable"), BatchItem("b", "b", 0, "disable"))) { _, _ ->
                executed++; OperationResult(true, "Applied", changed = true)
            }
            assertEquals(1, executed)
            assertEquals(1, result.failureCount)
            assertTrue(state.value.batchesList.single().interrupted)
            assertNull(coordinator.active.value)
        } finally { owner.cancel() }
    }

}
