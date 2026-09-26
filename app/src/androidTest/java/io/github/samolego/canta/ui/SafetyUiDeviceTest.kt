package io.github.samolego.canta.ui

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ui.dialog.PackageActionConfirmation
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.ui.viewmodel.PackageActionRequest
import io.github.samolego.canta.util.apps.AppInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import rikka.shizuku.Shizuku

class SafetyUiDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun keyboardWarningCannotBeBypassedByOrdinaryUninstallConfirmation() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        val name = "com.android.inputmethod.latin"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = AppInfo.fromPackageInfo(CanaServices.getInstance().packageOps.getPackageInfo(name, 0)!!, context.packageManager, false)
        val request = PackageActionRequest(PackageAction.UNINSTALL, 0, listOf(app))
        val model = AppListViewModel()
        var consent: Set<String>? = null
        compose.setContent { CantaTheme {
            PackageActionConfirmation(request, model, false, {}, { _, _, _, warnings -> consent = warnings[name] })
        } }
        compose.waitUntil(40_000) { compose.onAllNodesWithText("Current or enabled keyboard:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Uninstall") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("I understand these warnings and want to continue.").performScrollTo().performClick()
        compose.onNode(hasText("Uninstall") and hasClickAction()).assertIsEnabled().performClick()
        assertTrue(consent.orEmpty().contains("keyboard:$name"))
    }
}
