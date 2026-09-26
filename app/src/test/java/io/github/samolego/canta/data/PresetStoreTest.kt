package io.github.samolego.canta.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import io.github.samolego.canta.data.proto.CantaPreset
import io.github.samolego.canta.data.proto.PresetsList
import io.github.samolego.canta.ops.PresetPreview
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.LockdownSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PresetStoreTest {
    @Test fun repeatedInitializationMigratesAndCollectsOnlyOnce() = runBlocking {
        val legacy = CantaPreset.newBuilder().setName("Legacy").addApps("old.app").build()
        val backing = CountingStore(PresetsList.newBuilder().addPresets(legacy).build())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = PresetStore(backing)
            val first = store.initialize(scope)
            assertSame(first, store.initialize(scope))
            val loaded = first.first { it.loaded }
            assertTrue(loaded.presets.single().uuid.isNotEmpty())
            assertEquals(setOf("old.app"), loaded.presets.single().apps)
            assertEquals(1, backing.subscriptions)
            assertEquals(1, backing.writes)
        } finally { scope.cancel() }
    }

    @Test fun cancelDoesNotWriteAndConfirmationPersistsExactlyTheReviewedRevision() = runBlocking {
        val directory = Files.createTempDirectory("cana-preset").toFile()
        val job = SupervisorJob()
        val reopenedJob = SupervisorJob()
        try {
            val file = directory.resolve("presets.pb")
            val store = PresetStore(DataStoreFactory.create(PresetsListSerializer,
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
            val original = CantaPresetData("Before", "Saved", 10, setOf("old.app"), uuid = "same-id")
            assertTrue(store.savePreset(original))
            val imported = original.copy(name = "After", apps = setOf("new.app"), profileKind = "WORK",
                lockdown = listOf(LockdownSettings("privacy.app", denyMetered = true, blockNetwork = true)))
            val review = PresetPreview.reviewImport(imported, store.presetsFlow.first(), emptyList())
            assertEquals(original, store.presetsFlow.first().single())
            assertEquals(PresetImportResult.SAVED, store.saveReviewedImport(review))
            job.cancelAndJoin()
            val reopened = PresetStore(DataStoreFactory.create(PresetsListSerializer,
                scope = CoroutineScope(Dispatchers.IO + reopenedJob), produceFile = { file }))
            assertEquals(imported, reopened.presetsFlow.first().single())
        } finally { job.cancelAndJoin(); reopenedJob.cancelAndJoin(); directory.deleteRecursively() }
    }

    @Test fun changedSavedPresetRequiresAnotherReviewInsteadOfBeingOverwritten() = runBlocking {
        val store = PresetStore(CountingStore(PresetsList.getDefaultInstance()))
        val original = CantaPresetData("Original", "", 1, setOf("old.app"), uuid = "same-id")
        assertTrue(store.savePreset(original))
        val review = PresetPreview.reviewImport(original.copy(apps = setOf("new.app")), listOf(original), emptyList())
        val concurrentEdit = original.copy(description = "Edited after review")
        assertTrue(store.updatePreset(original, concurrentEdit))
        assertEquals(PresetImportResult.REVIEW_CHANGED, store.saveReviewedImport(review))
        assertEquals(concurrentEdit, store.presetsFlow.first().single())
    }

    @Test fun newIdentityCollisionAfterReviewIsNeverSilentlyDuplicated() = runBlocking {
        val store = PresetStore(CountingStore(PresetsList.getDefaultInstance()))
        val imported = CantaPresetData("Import", "", 1, setOf("new.app"), uuid = "same-id")
        val review = PresetPreview.reviewImport(imported, emptyList(), emptyList())
        val concurrent = imported.copy(name = "Other preset")
        assertTrue(store.savePreset(concurrent))
        assertEquals(PresetImportResult.REVIEW_CHANGED, store.saveReviewedImport(review))
        assertEquals(concurrent, store.presetsFlow.first().single())
    }

    private class CountingStore(initial: PresetsList) : DataStore<PresetsList> {
        private val contents = MutableStateFlow(initial)
        var subscriptions = 0
        var writes = 0
        override val data: Flow<PresetsList> = contents.onStart { subscriptions++ }
        override suspend fun updateData(transform: suspend (t: PresetsList) -> PresetsList): PresetsList {
            writes++
            return transform(contents.value).also { contents.value = it }
        }
    }
}
