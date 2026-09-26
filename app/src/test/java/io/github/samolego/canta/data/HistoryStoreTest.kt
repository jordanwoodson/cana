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

    @Test fun unknownMutationAndCompletedAutomaticRecoverySurviveReopening() = runBlocking {
        val dir = Files.createTempDirectory("cana-recovery").toFile()
        val job = SupervisorJob()
        val next = SupervisorJob()
        try {
            val file = dir.resolve("history.pb")
            val store = HistoryStore(DataStoreFactory.create(HistorySerializer,
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
            val pending = OperationRecord.newBuilder().setId("unknown").setBatchId("unknown")
                .setUserId(10).setPackageName("test.app").setAction("disable")
                .setPreviousState("{\"enabledSetting\":0}").build()
            store.append(pending)
            val result = io.github.samolego.canta.ops.packageOutcome(pending.previousState, null,
                io.github.samolego.canta.ops.OperationResult(true, "Applied"), "Unknown")
            store.complete("unknown", result.success, result.message, "{}", result.changed)
            store.append(pending.toBuilder().setId("recovered").setUndoOf("unknown").build())
            store.complete("recovered", false, "Manual work remains", "{}", false, recoveryComplete = true)
            job.cancelAndJoin()
            val reopened = HistoryStore(DataStoreFactory.create(HistorySerializer,
                scope = CoroutineScope(Dispatchers.IO + next), produceFile = { file }))
            val records = reopened.records.first()
            assertFalse(records.first().success)
            assertTrue(records.first().changed)
            assertTrue(records.last().recoveryComplete)
            assertTrue(io.github.samolego.canta.ops.ManagementPolicy.lastBatch(records).isEmpty())
            assertTrue(io.github.samolego.canta.ops.RestoreScript.generate(records).contains("'default-state'"))
        } finally { job.cancelAndJoin(); next.cancelAndJoin(); dir.deleteRecursively() }
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
