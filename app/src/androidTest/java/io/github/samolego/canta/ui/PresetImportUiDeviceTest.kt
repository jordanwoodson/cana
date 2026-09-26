package io.github.samolego.canta.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.datastore.core.DataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.R
import io.github.samolego.canta.data.PresetImportResult
import io.github.samolego.canta.data.PresetStore
import io.github.samolego.canta.data.PresetsListSerializer
import io.github.samolego.canta.ops.PresetImportReview
import io.github.samolego.canta.ops.PresetPreview
import io.github.samolego.canta.ops.PresetProfileInventory
import io.github.samolego.canta.ui.dialog.preset.PresetImportReviewDialog
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.LockdownSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Storage fixtures only: these tests never call privileged package or privacy operations. */
class PresetImportUiDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun cancelPreservesSavedPresetAndUpdatePersistsReviewedContent() = runBlocking {
        val file = File(context.cacheDir, "preset-review-${UUID.randomUUID()}.pb")
        val job = SupervisorJob()
        try {
            val store = PresetStore(DataStoreFactory.create(PresetsListSerializer,
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
            val original = CantaPresetData("Original", "", 1, setOf("old.app"), uuid = "same-id")
            assertTrue(store.savePreset(original))
            val imported = original.copy(name = "Reviewed", apps = setOf("new.app"),
                lockdown = listOf(LockdownSettings("privacy.app", denyMetered = true)), profileKind = "WORK")
            val review = PresetPreview.reviewImport(imported, listOf(original), listOf(
                PresetProfileInventory(0, "Personal", "PERSONAL", setOf("new.app"))))
            var visible by mutableStateOf<PresetImportReview?>(review)
            compose.setContent { CantaTheme {
                visible?.let { current -> PresetImportReviewDialog(current, false, false, null,
                    onConfirm = { runBlocking { assertEquals(PresetImportResult.SAVED, store.saveReviewedImport(current)) }; visible = null },
                    onDismiss = { visible = null }) }
            } }
            compose.onNodeWithText(context.getString(R.string.preset_import_added_removal, "new.app")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.preset_import_removed_removal, "old.app")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.preset_profile_mismatch)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.preset_import_missing, "privacy.app")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
            assertEquals(original, store.presetsFlow.first().single())
            compose.runOnIdle { visible = review }
            compose.onNodeWithText(context.getString(R.string.preset_import_update)).performClick()
            assertEquals(imported, store.presetsFlow.first().single())
        } finally { job.cancelAndJoin(); file.delete() }
    }

    @Test fun checkingInventoryDisablesConfirmationButAllowsCancel() {
        val preset = CantaPresetData("New", "", 1, emptySet(), uuid = "new-id")
        var dismissed = false
        compose.setContent { CantaTheme {
            PresetImportReviewDialog(PresetPreview.reviewImport(preset, emptyList(), emptyList()),
                checking = true, saving = false, error = null, onConfirm = { error("Must not save while checking") }, onDismiss = { dismissed = true })
        } }
        compose.onNodeWithText(context.getString(R.string.import_button)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        assertTrue(dismissed)
    }
}
