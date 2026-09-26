package io.github.samolego.canta.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.dialog.PrivacyDisconnectedNotice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PrivacyDisconnectedUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun disconnectedStateHasOneExplanationAndWorkingConnectAndRetryActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var connections = 0
        var retries = 0
        compose.setContent { MaterialTheme { PrivacyDisconnectedNotice({ connections++ }, { retries++ }) } }
        compose.onNodeWithText(context.getString(R.string.privacy_disconnected_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.privacy_connect)).performClick()
        compose.onNodeWithText(context.getString(R.string.privacy_retry)).performClick()
        compose.runOnIdle { assertEquals(1, connections); assertEquals(1, retries) }
    }
}
