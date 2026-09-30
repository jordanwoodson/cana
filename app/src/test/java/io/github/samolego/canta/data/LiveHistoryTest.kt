package io.github.samolego.canta.data

import androidx.datastore.core.DataStoreFactory
import io.github.samolego.canta.data.proto.OperationRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LiveHistoryTest {
    @Test fun runningGrantIsNotRecoveryButAnAbandonedGrantIs() = runBlocking {
        withStore { store ->
            store.withActiveOperation("grant") {
                store.append(grant("grant"))
                assertFalse(store.state.first().needsRecoveryReview)
                // The durable journal remains available to safety/recovery consumers.
                assertFalse(store.records.first().single().completed)
            }
            assertTrue(store.state.first().needsRecoveryReview)
        }
    }

    @Test fun liveGrantDoesNotHideOtherInterruptedWork() = runBlocking {
        withStore { store ->
            store.append(grant("old"))
            store.withActiveOperation("new") {
                store.append(grant("new"))
                assertTrue(store.state.first().needsRecoveryReview)
            }
        }
    }

    @Test fun completionAndCancellationReleaseLiveOwnership() = runBlocking {
        withStore { store ->
            store.withActiveOperation("done") {
                store.append(grant("done"))
                store.complete("done", true, "Granted", "{}", true)
            }
            assertFalse(store.state.first().needsRecoveryReview)
            val started = CompletableDeferred<Unit>()
            val job = launch {
                store.withActiveOperation("cancelled") {
                    store.append(grant("cancelled"))
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
            started.await()
            assertFalse(store.state.first().needsRecoveryReview)
            job.cancelAndJoin()
            assertTrue(store.state.first().needsRecoveryReview)
        }
    }

    private fun grant(id: String) = OperationRecord.newBuilder().setId(id)
        .setPackageName("test.cana").setAction("self_grant_usage_stats").build()

    private suspend fun withStore(block: suspend (HistoryStore) -> Unit) {
        val dir = Files.createTempDirectory("live-history").toFile()
        val job = SupervisorJob()
        try {
            block(HistoryStore(DataStoreFactory.create(HistorySerializer,
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { dir.resolve("history.pb") })))
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }
}
