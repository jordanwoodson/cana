package io.github.samolego.canta.ui

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ops.SystemControl
import io.github.samolego.canta.ui.screen.SystemPage
import io.github.samolego.canta.ui.theme.CantaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SystemUiDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun liveValuesValidationWarningAndOneTapRevert() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val services = CanaServices.getInstance()
        val before = services.system.snapshot(SystemControl.WIFI_SCAN).toString()
        compose.setContent { CantaTheme { SystemPage {} } }
        compose.onNodeWithText("These settings affect the whole device and every profile.").assertIsDisplayed()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("system-PRIVATE_DNS-state").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("system-dns-host").performScrollTo().performTextReplacement("https://invalid.test/")
        compose.onNodeWithTag("system-dns-apply").performScrollTo().performClick()
        compose.onNodeWithText("Enter a DNS hostname", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("system-portal-off").performScrollTo().performClick()
        compose.onNodeWithText("Turn off connectivity checks?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        val wasEnabled = org.json.JSONObject(before).getJSONObject("settings").optString("wifi_scan_always_enabled") == "1"
        compose.onNodeWithTag("system-WIFI_SCAN-${!wasEnabled}").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("system-WIFI_SCAN-revert").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("system-WIFI_SCAN-revert").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("system-WIFI_SCAN-revert").fetchSemanticsNodes().isEmpty() }
        assertEquals(before, services.system.snapshot(SystemControl.WIFI_SCAN).toString())
        compose.onNodeWithTag("system-DATA_SAVER-state").performScrollTo().assertIsDisplayed()
        Unit
    }
}
