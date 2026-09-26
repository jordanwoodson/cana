package io.github.samolego.canta.ui

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.extension.add
import io.github.samolego.canta.ui.component.SelectionActionBar
import io.github.samolego.canta.ui.dialog.ComponentsDialog
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.util.apps.Filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import rikka.shizuku.Shizuku

class DebloatUiDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unsafeSelectionDefaultsToDisableEvenWhenFilteredOut() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "io.github.jordanwoodson.cana.fixture"
        val model = AppListViewModel { _, _ -> JSONObject("""{"$name":{"removal":"Unsafe"}}""") }
        withContext(Dispatchers.Main) {
            model.loadInstalled(context.packageManager, context)
            model.onlySystem = false
            model.selectedApps.add(name)
            model.searchQuery = "no app matches this query"
            assertEquals(PackageAction.DISABLE, model.defaultAction)
        }
        compose.setContent { CantaTheme { SelectionActionBar(model, AppsType.INSTALLED) } }
        compose.onNodeWithText("1 selected · 1 hidden by this view").assertIsDisplayed()
        compose.onNodeWithText("Review actions").performClick()
        compose.onNodeWithText("Disable").assertIsDisplayed()
        for (action in listOf("Enable", "Suspend", "Unsuspend", "Uninstall keeping data", "Uninstall")) {
            compose.onNodeWithText(action).assertIsDisplayed()
        }
        compose.onNodeWithText("Uninstall keeping data").performClick()
        assertEquals(PackageAction.UNINSTALL_KEEP_DATA, model.pendingAction?.action)
        assertEquals(name, model.pendingAction?.apps?.single()?.packageName)
    }

    @Test fun ordinaryAppComponentsShowAndroidRestriction() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        compose.setContent { CantaTheme { ComponentsDialog("com.android.printspooler", 10, {}) } }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Root-backed Shizuku is required", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Root-backed Shizuku is required", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Profile: user 10").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).fetchSemanticsNodes().also { assertTrue(it.isNotEmpty()) }
        compose.onAllNodes(isToggleable())[0].assertIsNotEnabled()
        Unit
    }
}
