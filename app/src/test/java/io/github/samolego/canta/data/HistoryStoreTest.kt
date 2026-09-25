package io.github.samolego.canta.data

import androidx.datastore.core.DataStoreFactory
import io.github.samolego.canta.data.proto.OperationRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class HistoryStoreTest {
    @Test fun pendingAndPartialFailureSurviveReopeningStore() = runBlocking {
        val dir = Files.createTempDirectory("cana-history").toFile()
        val job = SupervisorJob()
        val reopenedJob = SupervisorJob()
        try {
            val file = dir.resolve("history.pb")
            val store = HistoryStore(DataStoreFactory.create(HistorySerializer,
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
            val pending = OperationRecord.newBuilder().setId("operation-1").setBatchId("batch-1")
                .setTimestampMs(123L).setUserId(10).setPackageName("com.example.app")
                .setAction("uninstall").setPreviousState("{\"installed\":true}").build()
            store.append(pending)
            assertFalse(store.records.first().single().completed)
            store.complete("operation-1", false, "Reset succeeded, uninstall denied", "{\"updated\":false}", true)
            job.cancelAndJoin()
            val reopened = HistoryStore(DataStoreFactory.create(HistorySerializer,
                scope = CoroutineScope(Dispatchers.IO + reopenedJob), produceFile = { file }))
            val actual = reopened.records.first().single()
            assertEquals(10, actual.userId)
            assertEquals("batch-1", actual.batchId)
            assertEquals("{\"installed\":true}", actual.previousState)
            assertTrue(actual.completed)
            assertFalse(actual.success)
            assertTrue(actual.changed)
            assertEquals("Reset succeeded, uninstall denied", actual.resultMessage)
            assertEquals("{\"updated\":false}", actual.afterState)
        } finally {
            job.cancelAndJoin()
            reopenedJob.cancelAndJoin()
            dir.deleteRecursively()
        }
    }

    @Test fun cannotCompleteAnUnknownOperation() = runBlocking {
        val dir = Files.createTempDirectory("cana-history").toFile()
        val job = SupervisorJob()
        try {
            val store = HistoryStore(DataStoreFactory.create(HistorySerializer,
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { dir.resolve("history.pb") }))
            var rejected = false
            try { store.complete("missing", true, "OK", "{}", true) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertTrue(store.records.first().isEmpty())
        } finally {
            job.cancelAndJoin()
            dir.deleteRecursively()
        }
    }
}
