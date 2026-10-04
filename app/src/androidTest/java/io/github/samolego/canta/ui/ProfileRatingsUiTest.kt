package io.github.samolego.canta.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.samolego.canta.ui.component.AppTile
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.apps.AppInfo
import io.github.samolego.canta.util.apps.UserProfile
import org.junit.Rule
import org.junit.Test

class ProfileRatingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingProfilesKeepsTheNormalDebloatListBadge() {
        val data = BloatData(null, "Fixture general advice", RemovalRecommendation.RECOMMENDED)
        val app = AppInfo("Play services", "com.google.android.gms", "1", 1,
            isSystemApp = true, isUninstalled = false, isDisabled = false, bloatData = data)
        val userId = mutableStateOf(UserProfile.currentUserId)
        compose.setContent { CantaTheme { AppTile(appInfo = app, userId = userId.value, isSelected = false,
            onCheckChanged = {}, onShowDialog = {}) } }
        compose.onNodeWithText("Recommended").assertIsDisplayed()
        compose.onNodeWithText("Keep for work").assertDoesNotExist()
        compose.runOnIdle { userId.value = 10 }
        compose.onNodeWithText("Recommended").assertIsDisplayed()
        compose.onNodeWithText("Keep for work").assertDoesNotExist()
        compose.onNodeWithText("Expert").assertDoesNotExist()
        compose.runOnIdle { userId.value = UserProfile.currentUserId }
        compose.onNodeWithText("Recommended").assertIsDisplayed()
        compose.onNodeWithText("Keep for work").assertDoesNotExist()
    }
}
