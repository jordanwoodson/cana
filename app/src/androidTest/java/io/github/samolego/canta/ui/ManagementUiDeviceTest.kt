package io.github.samolego.canta.ui

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.ui.dialog.preset.PresetApplyDialog
import io.github.samolego.canta.ui.menu.FiltersMenu
import io.github.samolego.canta.ui.screen.HistoryPage
import io.github.samolego.canta.ui.dialog.PackageActionDialogs
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.ui.viewmodel.PackageActionRequest
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.LockdownSettings
import io.github.samolego.canta.util.shizuku.ShizukuUserUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import rikka.shizuku.Shizuku
import java.util.UUID

class ManagementUiDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val pkg = "io.github.jordanwoodson.cana.fixture"
    private val services get() = CanaServices.getInstance()
    private suspend fun ready() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
    }
    @Test fun historyUndoRestoresWorkAppAndReportsResult() = runBlocking {
        ready()
        val batch = UUID.randomUUID().toString()
        assertTrue(services.packageOps.setEnabled(pkg, 10, false, batchId = batch).success)
        val host = SnackbarHostState()
        compose.setContent { CantaTheme {
            CompositionLocalProvider(LocalCanaSnackbar provides host) { HistoryPage {} }
        } }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("history-undo-$batch").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("history-undo-$batch").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Undo").onLast().performClick()
        compose.waitUntil(60_000) { runBlocking { services.packageOps.getPackageInfo(pkg, 10)?.applicationInfo?.enabled == true } }
        assertTrue(services.packageOps.getPackageInfo(pkg, 10)!!.applicationInfo!!.enabled)
        assertTrue(services.packageOps.getPackageInfo(pkg, 0)!!.applicationInfo!!.enabled)
    }
    @Test fun successfulWorkDisableCanBeUndoneFromSnackbar() = runBlocking {
        ready()
        val app = services.inventory.snapshot(10).apps.first { it.packageName == pkg }
        assertTrue(services.packageOps.getPackageInfo(pkg, 10)!!.applicationInfo!!.enabled)
        assertTrue(services.packageOps.getPackageInfo(pkg, 0)!!.applicationInfo!!.enabled)
        services.profiles.refresh()
        val model = AppListViewModel { _, _ -> JSONObject() }
        model.pendingAction = PackageActionRequest(PackageAction.DISABLE, 10, listOf(app))
        val host = SnackbarHostState()
        try {
            compose.setContent { CantaTheme {
                CanaMessageCollector(services.uiMessages, host)
                CompositionLocalProvider(LocalCanaSnackbar provides host) {
                    Scaffold(snackbarHost = { SnackbarHost(host) }) { innerPadding ->
                        Box(Modifier.padding(innerPadding)) { PackageActionDialogs(model) }
                    }
                }
            } }
            compose.waitUntil(60_000) {
                compose.onAllNodesWithText("Disable").fetchSemanticsNodes().any {
                    !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
                }
            }
            compose.onNodeWithText("Disable").performClick()
            compose.waitUntil(60_000) {
                compose.onAllNodesWithText("Disabled 1 app").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Undo").performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithText("Undo").onLast().performClick()
            compose.waitUntil(60_000) {
                runBlocking { services.packageOps.getPackageInfo(pkg, 10)?.applicationInfo?.enabled == true }
            }
            assertTrue(services.packageOps.getPackageInfo(pkg, 0)!!.applicationInfo!!.enabled)
        } finally {
            if (services.packageOps.getPackageInfo(pkg, 10)?.applicationInfo?.enabled != true) {
                assertTrue(services.packageOps.setEnabled(pkg, 10, true).success)
            }
        }
        Unit
    }
    @Test fun allProfileApplyRequiresProfileHintConfirmation() = runBlocking {
        ready()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preset = CantaPresetData("Both profiles", "", 1, emptySet(), lockdown = listOf(LockdownSettings(pkg, denyMetered = true)), profileKind = "WORK")
        compose.setContent { CantaTheme { PresetApplyDialog(preset, 10, {}) } }
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Apply to all profiles").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.preset_all_profiles)).performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size == 2 }
        val expectedProfiles = context.getString(R.string.preset_apply_profiles,
            listOf(context.getString(R.string.profile_personal), context.getString(R.string.profile_work)).joinToString())
        compose.onNodeWithText(expectedProfiles).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Apply Preset")[1].assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.safety_acknowledge)).performScrollTo().performClick()
        compose.waitUntil(60_000) { !compose.onAllNodesWithText("Apply Preset")[1].fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        compose.onAllNodesWithText("Apply Preset")[1].performClick()
        val expectedOutcome = context.getString(R.string.batch_outcome_counts, 2, 0, 0, 0)
        compose.waitUntil(90_000) { compose.onAllNodesWithText(expectedOutcome).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(expectedOutcome).performScrollTo().assertIsDisplayed()
        val records = services.undo.lastBatch()
        assertEquals(setOf(0, 10), records.map { it.userId }.toSet())
        assertEquals(0, services.undo.undo(records).failureCount)
    }
    @Test fun workProfileHidesUsageSortButKeepsSizeSort() = runBlocking {
        ready()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = AppListViewModel { _, _ -> JSONObject() }
        val profiles = ShizukuUserUtils.getUsers()
        try {
            model.selectUser(profiles.single { it.id == 10 }, context.packageManager, context)
            compose.setContent { CantaTheme { FiltersMenu(true, {}, model, AppsType.INSTALLED) } }
            compose.onNodeWithText("Size").assertIsDisplayed()
            compose.onNodeWithText("Last used").assertDoesNotExist()
            assertFalse(model.usageAvailable)
        } finally { model.selectUser(profiles.single { it.id == 0 }, context.packageManager, context) }
    }
}
