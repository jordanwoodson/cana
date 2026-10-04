package io.github.samolego.canta.ui

import android.graphics.Bitmap
import android.os.Build
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.R
import io.github.samolego.canta.data.PresetStore
import io.github.samolego.canta.data.batchDataStore
import io.github.samolego.canta.data.historyDataStore
import io.github.samolego.canta.data.privacyDataStore
import io.github.samolego.canta.data.proto.NetworkBlock
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.data.proto.PresetsList
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ops.PrivacyAction
import io.github.samolego.canta.ui.screen.HistoryPage
import io.github.samolego.canta.ui.screen.PresetsPage
import io.github.samolego.canta.ui.screen.PrivacyDashboardPage
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PresetsViewModel
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.apps.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Populated release screens on an emulator, at its native font scale. Fixtures only change
 * app-owned stores, whose complete protobuf snapshots are restored in finally. No confirmation
 * that executes a package, system, undo, or privacy change is clicked. Privacy fixtures have no
 * consent/identity and require review, so background reconciliation cannot apply them either.
 */
@SdkSuppress(minSdkVersion = 29)
class PopulatedReleaseUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val userId get() = UserProfile.currentUserId
    private val packageName = "example.cana.visual.calendar"

    @Test fun presetMenuNamesCopyAndCancelPreservesThePreset() = runBlocking {
        requireEmulator()
        val store = PresetStore.getInstance(context)
        store.initialize().first { it.loaded || it.error != null }.also { check(it.error == null) }
        val backing = presetBackingStore(store)
        val original = backing.data.first()
        val preset = CantaPresetData(
            "Work profile cleanup", "Optional apps saved for review before applying.", 1_790_000_000_000,
            setOf("example.cana.visual.weather", "example.cana.visual.partner"),
            uuid = "ui-populated-preset", profileKind = "WORK",
        )
        val models = withContext(Dispatchers.Main) { PresetsViewModel() to AppListViewModel() }
        var mounted by mutableStateOf(true)
        try {
            backing.updateData { it.toBuilder().clearPresets().build() }
            assertTrue(store.savePreset(preset))
            val seeded = backing.data.first()
            compose.setContent {
                CantaTheme {
                    CompositionLocalProvider(LocalCanaSnackbar provides SnackbarHostState()) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            if (mounted) PresetsPage(models.first,
                                onNavigateBack = { error("This test must remain in the preset library") },
                                appListViewModel = models.second)
                        }
                    }
                }
            }
            waitForText(preset.name)
            compose.onNodeWithText(preset.name).assertIsDisplayed()
            capture("01-presets-populated")
            compose.onNodeWithContentDescription(context.getString(R.string.more_options)).performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.preset_copy_action)).assertIsDisplayed()
            compose.onAllNodesWithText(context.getString(R.string.share)).assertCountEquals(0)
            capture("02-preset-actions")
            compose.onNodeWithText(context.getString(R.string.delete)).performClick()
            compose.onNodeWithText(context.getString(R.string.preset_delete_title)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.preset_delete_description, preset.name)).assertIsDisplayed()
            assertEquals(seeded, backing.data.first())
            capture("03-preset-delete-review")
            compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
            compose.onNodeWithText(context.getString(R.string.preset_delete_title)).assertDoesNotExist()
            compose.onNodeWithText(preset.name).assertIsDisplayed()
            assertEquals("Cancel must preserve the complete preset library", seeded, backing.data.first())
        } finally {
            runCatching { compose.runOnIdle { mounted = false } }
            withContext(NonCancellable + Dispatchers.Main) {
                models.first.viewModelScope.cancel()
                models.second.viewModelScope.cancel()
            }
            withContext(NonCancellable) { backing.updateData { original } }
        }
    }

    @Test fun historyShowsPartialStatusAndUndoReviewCanBeCancelled() = runBlocking {
        requireEmulator()
        val services = CanaServices.getInstance()
        check(services.batches.active.value == null)
        services.history.allRecords() // Wait for installation binding before capturing the store.
        val originalHistory = context.historyDataStore.data.first()
        val originalBatches = context.batchDataStore.data.first()
        val batchId = "ui-populated-history"
        val success = OperationRecord.newBuilder().setId("$batchId-success").setBatchId(batchId)
            .setTimestampMs(1_790_000_000_000).setSequenceNumber(1).setUserId(userId)
            .setPackageName(packageName).setAction("disable")
            .setPreviousState("{\"enabledSetting\":1}").setAfterState("{\"enabledSetting\":3}")
            .setCompleted(true).setSuccess(true).setChanged(true).setResultMessage("App disabled").build()
        val failure = success.toBuilder().setId("$batchId-failure").setSequenceNumber(2)
            .setPackageName("example.cana.visual.weather").setSuccess(false).setChanged(false)
            .setResultMessage("The app could not be changed").build()
        var mounted by mutableStateOf(true)
        try {
            context.batchDataStore.updateData { it.toBuilder().clearBatches().build() }
            val seeded = context.historyDataStore.updateData {
                it.toBuilder().clearRecords().clearQuarantinedRecords().addRecords(success).addRecords(failure)
                    .setNextSequence(2).build()
            }
            compose.setContent {
                CantaTheme {
                    CompositionLocalProvider(LocalCanaSnackbar provides SnackbarHostState()) {
                        if (mounted) HistoryPage {}
                    }
                }
            }
            waitForText(context.getString(R.string.history_status_partial))
            compose.onNodeWithText(context.getString(R.string.history_status_partial)).performScrollTo().assertIsDisplayed()
            capture("04-history-partial-status")
            compose.onNodeWithTag("history-undo-$batchId").performScrollTo().assertIsDisplayed().performClick()
            waitForText(context.getString(R.string.history_undo_changes))
            waitForNoProgress()
            compose.onNodeWithText(context.getString(R.string.history_undo_changes)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.undo_batch_description)).performScrollTo().assertIsDisplayed()
            capture("05-history-undo-review")
            val undoTarget = compose.onNode(hasText(packageName, substring = true) and hasAnyAncestor(isDialog()))
            undoTarget.performScrollTo().assertIsDisplayed()
            assertTextNotClipped(undoTarget)
            capture("05b-history-undo-target")
            compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
            compose.onNodeWithText(context.getString(R.string.history_undo_changes)).assertDoesNotExist()
            assertEquals("Review and Cancel must not change history", seeded, context.historyDataStore.data.first())
            assertTrue(context.batchDataStore.data.first().batchesList.isEmpty())
            check(services.batches.active.value == null)
        } finally {
            runCatching { compose.runOnIdle { mounted = false } }
            withContext(NonCancellable) {
                context.historyDataStore.updateData { originalHistory }
                context.batchDataStore.updateData { originalBatches }
            }
        }
    }

    @Test fun privacyGroupsTwoRestrictionsAndCancelPreservesBoth() = runBlocking {
        requireEmulator()
        val services = CanaServices.getInstance()
        services.privacy.initialize()
        val original = context.privacyDataStore.data.first()
        val block = NetworkBlock.newBuilder().setPackageName(packageName).setUserId(userId)
            .setAppId(0).setStatus("needs_review").build()
        var mounted by mutableStateOf(true)
        try {
            val seeded = context.privacyDataStore.updateData {
                it.toBuilder().clearNetworkBlocks().clearMeteredBlocks()
                    .addNetworkBlocks(block).addMeteredBlocks(block).build()
            }
            compose.setContent {
                CantaTheme {
                    CompositionLocalProvider(LocalCanaSnackbar provides SnackbarHostState()) {
                        if (mounted) PrivacyDashboardPage {}
                    }
                }
            }
            waitForText(packageName)
            waitForNoProgress()
            // The shared package and Review action occur once; each restriction retains its control.
            compose.onAllNodesWithText(packageName).assertCountEquals(1)
            compose.onAllNodesWithText(context.getString(R.string.privacy_review)).assertCountEquals(1)
            compose.onNodeWithText(packageName).performScrollTo().assertIsDisplayed()
            // performScrollTo only reveals the nearest edge. Align the header near the viewport
            // top so the first restriction is visible with the package it belongs to.
            val dashboardScroll = compose.onNode(hasScrollAction())
            val header = compose.onNodeWithText(packageName)
            val inset = 8 * context.resources.displayMetrics.density
            val distance = header.fetchSemanticsNode().boundsInRoot.top -
                dashboardScroll.fetchSemanticsNode().boundsInRoot.top - inset
            if (distance > 0) dashboardScroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
            compose.waitForIdle()
            header.assertIsDisplayed()
            compose.onNodeWithText(context.getString(PrivacyAction.METERED.title)).assertIsDisplayed()
            capture("06-privacy-grouped-app")
            listOf(PrivacyAction.METERED, PrivacyAction.NETWORK).forEachIndexed { index, action ->
                val title = context.getString(action.title)
                compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
                val forget = context.getString(R.string.privacy_forget_restriction, title)
                compose.onNodeWithText(forget).performScrollTo().assertIsDisplayed()
                capture("07${index}-privacy-restriction")
                compose.onNodeWithText(forget).performClick()
                compose.onNodeWithText(context.getString(R.string.privacy_forget_explanation)).assertIsDisplayed()
                capture("08${index}-privacy-forget-review")
                val explanation = compose.onNode(hasText(context.getString(R.string.privacy_forget_explanation)) and
                    hasAnyAncestor(isDialog()))
                assertTextNotClipped(explanation)
                scrollDialogToBottom()
                explanation.assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.cancel)).assertIsDisplayed()
                capture("08${index}b-privacy-forget-guidance")
                compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
                compose.onNodeWithText(context.getString(R.string.privacy_forget_explanation)).assertDoesNotExist()
                assertEquals("Cancel must preserve both saved restrictions", seeded, context.privacyDataStore.data.first())
            }
            compose.onNodeWithText(context.getString(R.string.privacy_review)).performScrollTo().assertIsDisplayed()
            capture("09-privacy-review-action")
        } finally {
            runCatching { compose.runOnIdle { mounted = false } }
            withContext(NonCancellable) { context.privacyDataStore.updateData { original } }
        }
    }

    private fun requireEmulator() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Use an emulator for populated UI fixtures" }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(90_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun waitForNoProgress() {
        compose.waitUntil(90_000) {
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                .fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
    }

    /** Assert actual rendered text rather than its full-but-potentially-clipped semantics label. */
    private fun assertTextNotClipped(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Text layout was not available", layouts.isNotEmpty())
        assertTrue("The warning or target text is clipped", layouts.all { !it.hasVisualOverflow })
    }

    private fun scrollDialogToBottom() {
        val body = compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog()))
        body.assert(hasScrollAction())
        val range = body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        body.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, range.maxValue()) }
        compose.waitForIdle()
        val finished = body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("The end of the warning must be reachable", finished.maxValue() - finished.value() <= 1f)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        awaitRenderedFrames()
        val config = context.resources.configuration
        val folder = File(context.getExternalFilesDir(null), "ui-populated/${config.fontScale}")
        check(folder.exists() || folder.mkdirs())
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(folder, "$name.png").outputStream().use {
                assertTrue("Could not save $name", screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
        val roots = compose.onAllNodes(isRoot())
        File(folder, "$name.txt").writeText(
            "Native configuration: ${config.screenWidthDp} x ${config.screenHeightDp}dp; fontScale=${config.fontScale}\n" +
                roots.fetchSemanticsNodes().indices.joinToString("\n") { roots[it].printToString() },
        )
    }

    private fun awaitRenderedFrames() {
        val drawn = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val window = checkNotNull(WindowInspector.getGlobalWindowViews().lastOrNull { it.isAttachedToWindow })
            window.postOnAnimation { window.postOnAnimation { drawn.countDown() } }
        }
        assertTrue("The screenshot window did not render", drawn.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        val roots = compose.onAllNodes(isRoot())
        roots.fetchSemanticsNodes().indices.forEach {
            roots[it].captureToImage().asAndroidBitmap().recycle()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun presetBackingStore(store: PresetStore): DataStore<PresetsList> = PresetStore::class.java
        .getDeclaredField("dataStore").apply { isAccessible = true }.get(store) as DataStore<PresetsList>
}
