package io.github.samolego.canta.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.dialog.TrackerSourceDialog
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrackerSourceUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cannotOverwriteSourceBeforeItsSavedValueLoads() {
        val loaded = CompletableDeferred<String>()
        var saved: String? = null
        compose.setContent { MaterialTheme {
            TrackerSourceDialog({}, {}, loadSource = { loaded.await() }, saveSource = { saved = it })
        } }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.tracker_url)
        compose.onNodeWithText(label).assertIsNotEnabled()
        loaded.complete("https://example.invalid/trackers.json")
        compose.waitUntil { compose.onAllNodesWithText("https://example.invalid/trackers.json").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals("https://example.invalid/trackers.json", saved) }
    }

    @Test fun draftSurvivesRecreationWithoutBeingReplacedBySavedSource() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme {
            TrackerSourceDialog({}, {}, loadSource = { "https://example.invalid/old.json" }, saveSource = {})
        } }
        compose.onNode(hasSetTextAction()).performTextReplacement("https://example.invalid/new.json")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("https://example.invalid/new.json").assertExists()
    }
}
