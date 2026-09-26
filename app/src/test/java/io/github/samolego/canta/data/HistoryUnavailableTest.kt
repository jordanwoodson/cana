package io.github.samolego.canta.data

import androidx.datastore.core.DataStoreFactory
import io.github.samolego.canta.data.proto.OperationRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class HistoryUnavailableTest {
    @Test fun corruptHistoryIsUnavailableToUiAndRefusesNewMutations() = runBlocking {
        val dir = Files.createTempDirectory("history-corrupt").toFile()
        val job = SupervisorJob()
        try {
            val file = dir.resolve("history.pb").apply { writeBytes(byteArrayOf(-1)) }
            val store = HistoryStore(DataStoreFactory.create(HistorySerializer,
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
            assertTrue(store.state.first().unavailable)
            assertTrue(runCatching { store.records.first() }.isFailure)
            assertTrue(runCatching { store.append(OperationRecord.newBuilder().setId("unsafe").build()) }.isFailure)
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }
}
