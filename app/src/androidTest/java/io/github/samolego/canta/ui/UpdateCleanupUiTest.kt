package io.github.samolego.canta.ui

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ui.dialog.PackageActionConfirmation
import io.github.samolego.canta.ui.component.fab.ExpandableFAB
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.ui.viewmodel.PackageActionRequest
import io.github.samolego.canta.util.apps.AppInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateCleanupUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sharedUpdateStartsSkippedAndRequiresExplicitSelection() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val services = CanaServices.getInstance()
        val ops = services.packageOps
        val name = "com.android.printspooler"
        try {
            assertTrue(ops.reinstall(name, 0).success)
            assertTrue(ops.reinstall(name, 10).success)
            assertTrue(services.shell.exec(listOf("pm", "install", "-r", "/data/local/tmp/cana-printspooler.apk"), 30_000).success)
            assertTrue(ops.uninstall(name, 10).success)
            val app = AppInfo.fromPackageInfo(ops.getPackageInfo(name, 10)!!,
                InstrumentationRegistry.getInstrumentation().targetContext.packageManager, true, otherUser = true)
            val request = PackageActionRequest(PackageAction.REMOVE_UPDATES, 10, listOf(app))
            var consent: Set<Int>? = null
            val model = AppListViewModel()
            compose.setContent {
                CantaTheme { PackageActionConfirmation(request, model, true, {}, { included, _, approvals, _ ->
                    assertEquals(setOf(name), included)
                    consent = approvals[name]
                }) }
            }
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Checking updates and other profiles…").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Profile: user 10").assertIsDisplayed()
            compose.onNode(hasText("Removing updates also downgrades", substring = true)).performScrollTo().assertIsDisplayed()
            compose.onNode(hasText("Apply reviewed changes") and hasClickAction()).assertIsNotEnabled()
            compose.onNodeWithText(app.name).performScrollTo().performClick()
            compose.onNodeWithText("I understand these warnings and want to continue.").performScrollTo().performClick()
            compose.onNode(hasText("Apply reviewed changes") and hasClickAction()).assertIsEnabled().performClick()
            assertEquals(setOf(0), consent)
            assertFalse(ops.inspectUpdates(name, 10).installed)
        } finally {
            ops.removeUpdates(name, 10, setOf(0))
            ops.reinstall(name, 0)
            ops.reinstall(name, 10)
        }
    }

    @Test fun expandableActionsHaveVisibleLabels() {
        var chosen = ""
        compose.setContent { CantaTheme { ExpandableFAB(topLabel = "Remove updates", bottomLabel = "Reinstall",
            onTopClick = { chosen = "cleanup" }, onBottomClick = { chosen = "reinstall" }) } }
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Remove updates").assertIsDisplayed()
        compose.onNodeWithText("Reinstall").assertIsDisplayed().performClick()
        assertEquals("reinstall", chosen)
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Remove updates").performClick()
        assertEquals("cleanup", chosen)
    }
}
