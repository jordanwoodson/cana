package io.github.samolego.canta.data

import androidx.datastore.core.DataStoreFactory
import io.github.samolego.canta.data.proto.OperationRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class HistoryIsolationTest {
    @Test fun restoredHistoryIsUnavailableBeforeBindingAndRetainedForReview() = runBlocking {
        val dir = Files.createTempDirectory("history-isolation").toFile()
        val job = SupervisorJob()
        try {
            val data = DataStoreFactory.create(HistorySerializer, scope = CoroutineScope(Dispatchers.IO + job), produceFile = { dir.resolve("history.pb") })
            val old = HistoryStore(data, installationId = "old-installation")
            old.append(OperationRecord.newBuilder().setId("old-action").setAction("disable").setPreviousState("{\"enabledSetting\":0}").build())
            val restored = HistoryStore(data, installationId = "new-installation")
            assertTrue(restored.records.first().isEmpty())
            restored.bindInstallation("new-installation")
            assertTrue(restored.records.first().isEmpty())
            assertEquals("old-action", restored.quarantinedRecords.first().single().id)
            restored.append(OperationRecord.newBuilder().setId("new-action").build())
            assertEquals(listOf("new-action"), restored.records.first().map { it.id })
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun archivedSuccessesRemainInRecoveryExportAndPendingRemainsLive() = runBlocking {
        val dir = Files.createTempDirectory("history-archive").toFile()
        val job = SupervisorJob()
        try {
            val data = DataStoreFactory.create(HistorySerializer, scope = CoroutineScope(Dispatchers.IO + job), produceFile = { dir.resolve("history.pb") })
            val store = HistoryStore(data, HistoryArchive(dir.resolve("archive")), completedLimit = 2)
            store.append(OperationRecord.newBuilder().setId("pending").setPreviousState("{}").build())
            store.markPending("pending", "Uncertain", "{}")
            for (index in 0..4) {
                store.append(OperationRecord.newBuilder().setId("complete-$index").setTimestampMs((10 - index).toLong()).setPreviousState("{}").build())
                store.complete("complete-$index", true, "Changed", "{}", true)
            }
            assertEquals(3, store.records.first().size)
            assertTrue(store.records.first().first { it.id == "pending" }.outcomeUnknown)
            assertEquals(6, store.allRecords().size)
            assertEquals(listOf("pending", "complete-0", "complete-1", "complete-2", "complete-3", "complete-4"), store.allRecords().map { it.id })
            assertEquals(6, HistoryStore(data, HistoryArchive(dir.resolve("archive")), 2).allRecords().size)
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }
}
