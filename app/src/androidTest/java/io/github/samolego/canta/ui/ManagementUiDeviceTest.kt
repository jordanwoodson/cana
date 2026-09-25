package io.github.samolego.canta.ui

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.ui.dialog.preset.PresetApplyDialog
import io.github.samolego.canta.ui.menu.FiltersMenu
import io.github.samolego.canta.ui.screen.HistoryPage
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
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
        compose.setContent { CantaTheme { HistoryPage {} } }
        compose.onNodeWithText("Undo last batch").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("1 succeeded · 0 failed").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(services.packageOps.getPackageInfo(pkg, 10)!!.applicationInfo!!.enabled)
        assertTrue(services.packageOps.getPackageInfo(pkg, 0)!!.applicationInfo!!.enabled)
    }
    @Test fun allProfileApplyRequiresProfileHintConfirmation() = runBlocking {
        ready()
        val preset = CantaPresetData("Both profiles", "", 1, emptySet(), lockdown = listOf(LockdownSettings(pkg, denyMetered = true)), profileKind = "WORK")
        compose.setContent { CantaTheme { PresetApplyDialog(preset, 10, {}) } }
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Apply to all profiles").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.waitUntil(60_000) { compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size == 2 }
        compose.onNodeWithText("Apply to users: 0, 10").assertIsDisplayed()
        compose.onAllNodesWithText("Apply Preset")[1].assertIsNotEnabled()
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.waitUntil(60_000) { !compose.onAllNodesWithText("Apply Preset")[1].fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        compose.onAllNodesWithText("Apply Preset")[1].performClick()
        compose.waitUntil(90_000) { compose.onAllNodesWithText("2 succeeded · 0 failed").fetchSemanticsNodes().isNotEmpty() }
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
            compose.setContent { CantaTheme { FiltersMenu(true, {}, model) } }
            compose.onNodeWithText("Sort by APK size").assertIsDisplayed()
            compose.onNodeWithText("Sort by last used").assertDoesNotExist()
            assertFalse(model.usageAvailable)
        } finally { model.selectUser(profiles.single { it.id == 0 }, context.packageManager, context) }
    }
}
