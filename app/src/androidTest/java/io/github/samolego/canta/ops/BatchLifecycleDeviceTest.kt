package io.github.samolego.canta.ops

import android.os.Build
import androidx.datastore.core.DataStoreFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.MainActivity
import io.github.samolego.canta.data.BatchSerializer
import io.github.samolego.canta.data.BatchStore
import io.github.samolego.canta.data.batchDataStore
import io.github.samolego.canta.data.historyDataStore
import io.github.samolego.canta.data.proto.OperationRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Lifecycle acceptance uses fake execution callbacks; no package or privacy setting is mutated. */
class BatchLifecycleDeviceTest {
    @Test fun terminalJournalPublishedBeforePendingItemStillSettlesTheBatch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = CanaServices.getInstance()
        services.batches.awaitReady()
        val oldBatches = context.batchDataStore.data.first()
        val oldHistory = context.historyDataStore.data.first()
        val id = UUID.randomUUID().toString()
        try {
            services.batchStore.append(id, "Late callback test", listOf(BatchItem("one", "example.callback", 0, "disable")))
            services.batchStore.started(id, "one")
            services.history.append(OperationRecord.newBuilder().setId(id).setBatchId(id).setPackageName("example.callback")
                .setAction("disable").setUserId(0).build())
            services.history.complete(id, true, "Verified", "{}", false)
            // Let the history observer see the terminal record while the item is still running.
            delay(250)
            services.batchStore.finished(id, "one", OperationResult(false, "Awaiting callback", outcomeUnknown = true))
            val settled = withTimeout(10_000) { services.batchStore.batches.first { list ->
                list.find { it.id == id }?.itemsList?.single()?.status == "completed"
            }.single { it.id == id }.itemsList.single() }
            assertTrue(settled.success)
            assertEquals("Verified", settled.message)
        } finally {
            context.batchDataStore.updateData { oldBatches }
            context.historyDataStore.updateData { oldHistory }
        }
    }

    @Test fun activityRecreationAndObserverCancellationDoNotStopAcceptedBatch() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = CanaServices.getInstance()
        services.batches.awaitReady()
        val original = context.batchDataStore.data.first()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val executed = mutableListOf<String>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val observer = launch(Dispatchers.Main) {
                    services.batches.run("Lifecycle test", listOf(BatchItem("a", "example.first", 0, "test"), BatchItem("b", "example.second", 0, "test"))) { item, _ ->
                        if (item.key == "a") { started.complete(Unit); release.await() }
                        executed += item.packageName
                        OperationResult(true, "Fixture callback completed")
                    }
                }
                withTimeout(30_000) { started.await() }
                val id = checkNotNull(services.batches.active.value).id
                scenario.recreate()
                observer.cancelAndJoin()
                assertEquals(id, services.batches.active.value?.id)
                release.complete(Unit)
                val completed = withTimeout(30_000) { services.batchStore.batches.first { list -> list.any { it.id == id && it.completed } }.single { it.id == id } }
                withTimeout(30_000) { services.batches.active.first { it == null } }
                assertEquals(listOf("example.first", "example.second"), executed)
                assertTrue(completed.itemsList.all { it.success && it.status == "completed" })
            }
        } finally {
            release.complete(Unit)
            withTimeout(30_000) { services.batches.active.first { it == null } }
            context.batchDataStore.updateData { original }
        }
    }

    @Test fun reopeningPersistedPlanReportsInterruptionWithoutExecutingIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "batch-restart-${UUID.randomUUID()}.pb")
        val oldOwner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val newOwner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val oldStore = BatchStore(DataStoreFactory.create(BatchSerializer, scope = oldOwner) { file })
            oldStore.append("interrupted", "Test", listOf(BatchItem("a", "example.first", 10, "test"), BatchItem("b", "example.second", 10, "test")))
            oldStore.started("interrupted", "a")
            oldOwner.coroutineContext[Job]!!.cancelAndJoin()
            val reopened = BatchStore(DataStoreFactory.create(BatchSerializer, scope = newOwner) { file })
            val coordinator = BatchCoordinator(reopened, newOwner)
            coordinator.awaitReady()
            val plan = reopened.batches.first().single()
            assertTrue(plan.completed && plan.interrupted && !plan.reviewAcknowledged)
            assertEquals(listOf("interrupted", "not_started"), plan.itemsList.map { it.status })
            assertNull(coordinator.active.value)
        } finally {
            oldOwner.coroutineContext[Job]!!.cancelAndJoin()
            newOwner.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
