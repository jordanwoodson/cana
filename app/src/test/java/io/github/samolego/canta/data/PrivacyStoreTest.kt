package io.github.samolego.canta.data

import androidx.datastore.core.DataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PrivacyStoreTest {
    @Test fun bothKindsSurviveReopenAndClearIndependentlyByProfile() = runBlocking {
        val directory = Files.createTempDirectory("cana-privacy").toFile()
        val job = SupervisorJob()
        val reopenedJob = SupervisorJob()
        try {
            val file = directory.resolve("privacy.pb")
            val store = PrivacyStore(DataStoreFactory.create(PrivacySerializer, scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
            store.setBlock("test.app", 10, 10153, true)
            store.setMetered("test.app", 0, 10153, true)
            store.setMetered("test.app", 10, 10153, true)
            job.cancelAndJoin()
            val reopened = PrivacyStore(DataStoreFactory.create(PrivacySerializer, scope = CoroutineScope(Dispatchers.IO + reopenedJob), produceFile = { file }))
            assertEquals(setOf(0, 10), reopened.meteredBlocks.first().map { it.userId }.toSet())
            reopened.setMetered("test.app", 10, 10153, false)
            assertEquals(0, reopened.meteredBlocks.first().single().userId)
            assertEquals(10, reopened.blocks.first().single().userId)
            reopened.setBlock("test.app", 10, 10153, false)
            assertTrue(reopened.blocks.first().isEmpty())
        } finally { job.cancelAndJoin(); reopenedJob.cancelAndJoin(); directory.deleteRecursively() }
    }
}
