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
        val title = compose.activity.getString(R.string.shizuku_required)
        val close = compose.activity.getString(R.string.close)
        val profile = compose.activity.getString(R.string.switch_profile)
        compose.onNodeWithContentDescription(profile).performClick()
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(close).performClick()
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNodeWithContentDescription(profile).performClick()
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
