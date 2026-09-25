package io.github.samolego.canta.data

import androidx.datastore.core.DataStoreFactory
import io.github.samolego.canta.ops.PackageInventory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ManagementStoreTest {
    @Test fun pendingChangesSurviveReopenAndInaccessibleProfilesKeepTheirBaseline() = runBlocking {
        val directory = Files.createTempDirectory("cana-management").toFile()
        val job = SupervisorJob()
        val nextJob = SupervisorJob()
        try {
            val file = directory.resolve("management.pb")
            val store = ManagementStore(DataStoreFactory.create(ManagementSerializer, scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
            val original = PackageInventory(10, setOf("old.app"), setOf("old.app"))
            store.observe("old", listOf(original), emptyList())
            store.observe("new", listOf(PackageInventory(0, emptySet(), emptySet())), emptyList())
            assertEquals("old", store.state.first().profilesList.single { it.userId == 10 }.fingerprint)
            val updated = PackageInventory(10, setOf("old.app", "new.app"), setOf("old.app", "new.app"))
            assertEquals("new.app", store.observe("new", listOf(updated), emptyList()).pendingList.single().packageName)
            job.cancelAndJoin()
            val reopened = ManagementStore(DataStoreFactory.create(ManagementSerializer, scope = CoroutineScope(Dispatchers.IO + nextJob), produceFile = { file }))
            assertEquals(1, reopened.observe("new", listOf(updated), emptyList()).pendingCount)
            reopened.dismiss(reopened.state.first().pendingList)
            assertEquals(0, reopened.observe("new", listOf(updated), emptyList()).pendingCount)
        } finally { job.cancelAndJoin(); nextJob.cancelAndJoin(); directory.deleteRecursively() }
    }
}
