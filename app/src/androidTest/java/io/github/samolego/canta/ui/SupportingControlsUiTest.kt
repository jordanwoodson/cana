package io.github.samolego.canta.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.samolego.canta.ui.component.CheckboxRow
import io.github.samolego.canta.ui.component.SettingsItem
import io.github.samolego.canta.ui.theme.CantaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SupportingControlsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingLabelAndSwitchAreOneToggleTarget() {
        var changes = 0
        compose.setContent {
            var checked by remember { mutableStateOf(false) }
            CantaTheme {
                SettingsItem(title = "Require authentication", isSwitch = true, checked = checked,
                    onCheckedChange = { checked = it; changes++ })
            }
        }

        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNodeWithText("Require authentication").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(1, changes) }
    }

    @Test fun acknowledgmentLabelTogglesItsCheckbox() {
        compose.setContent {
            var checked by remember { mutableStateOf(false) }
            CantaTheme {
                CheckboxRow(checked, { checked = it }) { Text("I understand the warning") }
            }
        }

        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNodeWithText("I understand the warning").assertIsOff().performClick().assertIsOn()
    }
}
