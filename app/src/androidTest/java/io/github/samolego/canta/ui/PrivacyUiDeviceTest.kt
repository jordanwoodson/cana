package io.github.samolego.canta.ui

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.data.PresetStore
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ops.PrivacyAction
import io.github.samolego.canta.ui.dialog.PrivacyDialog
import io.github.samolego.canta.ui.dialog.preset.PresetApplyDialog
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.LockdownSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import rikka.shizuku.Shizuku

class PrivacyUiDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val pkg = "io.github.jordanwoodson.cana.fixture"
    private suspend fun ready() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
    }
    @Test fun actualPermissionAndBackgroundValuesAndWorkProfileAreVisible() = runBlocking {
        ready()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { CantaTheme { PrivacyDialog(pkg, 10, {}) } }
        compose.waitUntil(90_000) { compose.onAllNodesWithTag("privacy-permission-android.permission.CAMERA").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.profile_work)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("privacy-permission-android.permission.CAMERA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Background activity").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("All network access").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not blocked").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Revoke 0").performScrollTo().assertIsNotEnabled()
        Unit
    }
    @Test fun backgroundSwitchOpensReviewBeforeChangingLiveState() = runBlocking {
        ready()
        val services = CanaServices.getInstance()
        val before = services.privacy.snapshot(pkg, 10, PrivacyAction.BACKGROUND).toString()
        compose.setContent { CantaTheme { PrivacyDialog(pkg, 10, {}) } }
        compose.waitUntil(90_000) { compose.onAllNodesWithTag("privacy-switch-background").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("privacy-switch-background").performScrollTo().performClick()
        compose.onNodeWithText("Review privacy change").assertIsDisplayed()
        assertEquals(before, services.privacy.snapshot(pkg, 10, PrivacyAction.BACKGROUND).toString())
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(before, services.privacy.snapshot(pkg, 10, PrivacyAction.BACKGROUND).toString())
    }
    @Test fun lockdownPresetRoundtripAndApplyOnlyAffectCapturedWorkProfile() = runBlocking {
        ready()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = CanaServices.getInstance()
        val store = PresetStore(context)
        val original = CantaPresetData("Privacy test", "Work only", 1, emptySet(), uuid = java.util.UUID.randomUUID().toString(),
            lockdown = listOf(LockdownSettings(pkg, denyMetered = true)))
        assertTrue(store.savePreset(original))
        val preset = store.presetsFlow.first().last { it.uuid == original.uuid }
        assertEquals(original, preset)
        val beforePeer = services.privacy.snapshot(pkg, 0, PrivacyAction.METERED)
        val previousIds = services.history.records.first().map { it.id }.toSet()
        try {
        compose.setContent { CantaTheme { PresetApplyDialog(preset, 10, {}) } }
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Apply Preset").fetchSemanticsNodes().size == 2 &&
            compose.onAllNodesWithText("Apply Preset")[1].fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() }
        val expectedProfile = context.getString(R.string.preset_apply_profiles, context.getString(R.string.profile_work))
        compose.onNodeWithText(expectedProfile).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Apply Preset")[1].performClick()
        val expected = context.getString(R.string.batch_outcome_counts, 1, 0, 0, 0)
        compose.waitUntil(60_000) { compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(expected).performScrollTo().assertIsDisplayed()
        assertEquals(beforePeer.toString(), services.privacy.snapshot(pkg, 0, PrivacyAction.METERED).toString())
        } finally {
            services.history.records.first().filter { it.id !in previousIds && it.packageName == pkg &&
                it.action == "metered" && it.userId == 10 && it.undoOf.isEmpty() && it.changed }.asReversed().forEach { record ->
                val undo = services.privacy.undo(record)
                assertTrue(undo.message, undo.success)
            }
            assertTrue(store.deletePreset(preset))
        }
    }
}
