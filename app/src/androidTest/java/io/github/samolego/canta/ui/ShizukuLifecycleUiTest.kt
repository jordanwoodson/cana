package io.github.samolego.canta.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.samolego.canta.MainActivity
import io.github.samolego.canta.R
import io.github.samolego.canta.data.SettingsStore
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

class ShizukuLifecycleUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun profileAuthorizationSurvivesRecreationAndCanBeClosedAndReopened() {
        assumeFalse("Requires an unauthorized app", ShizukuPermission.isCantaAuthorized())
        runBlocking { SettingsStore.getInstance().setDisableRiskDialog(true) }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Welcome to Cana").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("profile-chip").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Welcome to Cana").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("I understand removing system apps can break my device").performClick()
            compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Skip for now").performClick()
        }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("profile-chip").fetchSemanticsNodes().isNotEmpty() }
        val title = compose.activity.getString(R.string.shizuku_required)
        val close = compose.activity.getString(R.string.close)
        compose.onNodeWithTag("profile-chip").performClick()
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(close).performClick()
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNodeWithTag("profile-chip").performClick()
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
