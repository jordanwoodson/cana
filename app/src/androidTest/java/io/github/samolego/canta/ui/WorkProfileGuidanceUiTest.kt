package io.github.samolego.canta.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.samolego.canta.ops.SafetyAssessment
import io.github.samolego.canta.ops.WorkProfileGuidance
import io.github.samolego.canta.ui.component.AppTile
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.apps.AppInfo
import io.github.samolego.canta.util.apps.UserProfile
import org.junit.Rule
import org.junit.Test

class WorkProfileGuidanceUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingProfileShowsItsOwnGuidanceAndRisk() {
        val data = BloatData(null, "Fixture general advice", RemovalRecommendation.RECOMMENDED)
        val personal = AppInfo("Play services", "com.google.android.gms", "1", 1,
            isSystemApp = true, isUninstalled = false, isDisabled = false, bloatData = data)
        val app = mutableStateOf(personal)
        compose.setContent { CantaTheme { AppTile(appInfo = app.value, isSelected = false,
            onCheckChanged = {}, onShowDialog = {}) } }
        compose.onNodeWithText("Recommended").assertIsDisplayed()
        compose.onNodeWithText("Keep for work").assertDoesNotExist()
        compose.runOnIdle { app.value = personal.copy(profileGuidance = WorkProfileGuidance.assess(
            personal.packageName, UserProfile.Kind.WORK, data, SafetyAssessment())) }
        compose.onNodeWithText("Keep for work").assertIsDisplayed()
        compose.onNodeWithText("Expert").assertIsDisplayed()
        compose.onNodeWithText("Recommended").assertDoesNotExist()
        compose.runOnIdle { app.value = personal }
        compose.onNodeWithText("Recommended").assertIsDisplayed()
        compose.onNodeWithText("Keep for work").assertDoesNotExist()
    }
}
