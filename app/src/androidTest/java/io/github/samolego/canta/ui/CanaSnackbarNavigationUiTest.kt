package io.github.samolego.canta.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.samolego.canta.ops.UiMessages
import io.github.samolego.canta.ui.theme.CantaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanaSnackbarNavigationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun completionAfterSourceClosesAppearsOnDestination() {
        val messages = UiMessages()
        var destination by mutableStateOf(false)
        compose.setContent {
            CantaTheme {
                val host = remember { SnackbarHostState() }
                CanaMessageCollector(messages, host)
                Scaffold(snackbarHost = { SnackbarHost(host) }) { padding ->
                    Text(if (destination) "Destination" else "Source", Modifier.padding(padding))
                }
            }
        }
        compose.runOnIdle { destination = true }
        compose.onNodeWithText("Destination").assertIsDisplayed()
        compose.runOnIdle { messages.post("Finished after navigation") }
        compose.onNodeWithText("Finished after navigation").assertIsDisplayed()
    }
}
