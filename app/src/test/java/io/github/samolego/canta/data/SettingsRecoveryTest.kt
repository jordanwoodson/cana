package io.github.samolego.canta.data

import androidx.datastore.core.DataStoreFactory
import io.github.samolego.canta.data.proto.AppSettings
import io.github.samolego.canta.util.ANDROID_DEBLOAT_LIST_URL
import io.github.samolego.canta.util.DEFAULT_BLOAT_URL
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRecoveryTest {
    @get:Rule val folder = TemporaryFolder()

    private suspend fun open(file: File, block: suspend (SettingsStore) -> Unit) {
        val job = SupervisorJob()
        val store = DataStoreFactory.create(AppSettingsSerializer, corruptionHandler = settingsCorruptionHandler,
            migrations = listOf(ListSourceMigration), scope = CoroutineScope(Dispatchers.IO + job)) { file }
        try { block(SettingsStore(store)) } finally { job.cancelAndJoin() }
    }

    @Test fun missingFileStartsNormallyButCorruptFilesStayLockedAcrossRestarts() = runBlocking {
        open(File(folder.root, "fresh.pb")) { assertFalse(it.snapshot().settingsRecoveryRequired) }
        for ((i, bytes) in listOf(byteArrayOf(), byteArrayOf(0x80.toByte())).withIndex()) {
            val file = folder.newFile("corrupt-$i.pb").apply { writeBytes(bytes) }
            open(file) { assertTrue(it.snapshot().settingsRecoveryRequired && it.snapshot().authEnabled) }
            open(file) {
                assertTrue(it.snapshot().settingsRecoveryRequired)
                it.acknowledgeSettingsRecovery()
            }
            open(file) { assertTrue(!it.snapshot().settingsRecoveryRequired && it.snapshot().authEnabled) }
        }
    }

    @Test fun explicitArchiveSelectionSurvivesAfterLegacyDefaultMigratesOnce() = runBlocking {
        val file = folder.newFile("legacy.pb")
        file.writeBytes(AppSettings.newBuilder().setBloatListUrl(ANDROID_DEBLOAT_LIST_URL)
            .setBloatCacheUrl(ANDROID_DEBLOAT_LIST_URL).setBloatEtag("old").setAuthEnabled(true).build().toByteArray())
        open(file) {
            val migrated = it.snapshot()
            assertEquals(DEFAULT_BLOAT_URL, migrated.bloatListUrl)
            assertTrue(migrated.listSourceMigrated && migrated.authEnabled && migrated.bloatEtag.isEmpty())
            it.setBloatListUrl(ANDROID_DEBLOAT_LIST_URL)
        }
        open(file) { assertEquals(ANDROID_DEBLOAT_LIST_URL, it.snapshot().bloatListUrl) }
    }
}
