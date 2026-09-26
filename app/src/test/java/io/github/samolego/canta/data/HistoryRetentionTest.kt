package io.github.samolego.canta.data

import androidx.datastore.core.DataStoreFactory
import io.github.samolego.canta.data.proto.OperationRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class HistoryRetentionTest {
    @Test fun boundedDiagnosticsRetainPendingAndUnresolvedFailures() = runBlocking {
        val dir = Files.createTempDirectory("history-retention").toFile()
        val job = SupervisorJob()
        try {
            val store = HistoryStore(DataStoreFactory.create(HistorySerializer,
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { dir.resolve("history.pb") }))
            fun record(id: String) = OperationRecord.newBuilder().setId(id).setAction("disable")
                .setPackageName("test.app").setPreviousState("{\"enabledSetting\":0}").build()
            store.append(record("pending"))
            store.append(record("partial"))
            store.complete("partial", false, "Failed after change", "{\"enabledSetting\":3}", true)
            for (index in 0 until 510) {
                store.append(record("unchanged-$index"))
                store.complete("unchanged-$index", true, "No change", "{\"enabledSetting\":0}", false)
            }
            val records = store.records.first()
            assertTrue(records.size <= 502)
            assertTrue(records.any { it.id == "pending" && !it.completed })
            assertTrue(records.any { it.id == "partial" && it.changed })
            assertTrue(records.any { it.id == "unchanged-509" })
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }
}
