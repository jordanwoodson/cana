package io.github.samolego.canta.ui

import android.text.format.Formatter
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.component.AppTile
import io.github.samolego.canta.util.apps.AppInfo
import org.junit.Rule
import org.junit.Test

class AppSizeUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val baseApp get() = AppInfo("Example", "example.app", "1", 1,
        isSystemApp = false, isUninstalled = false, isDisabled = false, bloatData = null, apkSizeBytes = 2_000_000)

    @Test fun ordinaryAppRowShowsApkSizeWithoutAnUpdateLine() {
        compose.setContent { MaterialTheme { AppTile(appInfo = baseApp, isSelected = false,
            onCheckChanged = {}, onShowDialog = {}) } }
        compose.onNodeWithText(context.getString(R.string.app_apk_size, Formatter.formatFileSize(context, 2_000_000)))
            .assertIsDisplayed()
        compose.onNodeWithText("System update:", substring = true).assertDoesNotExist()
    }

    @Test fun removedUpdatedSystemAppRowShowsUnknownTotalAndKnownUpdate() {
        compose.setContent { MaterialTheme { AppTile(appInfo = baseApp.copy(
            isSystemApp = true, isUninstalled = true, isUpdatedSystemApp = true,
            apkSizeBytes = 0, updateSizeBytes = 1_000_000), isSelected = false,
            onCheckChanged = {}, onShowDialog = {}) } }
        compose.onNodeWithText(context.getString(R.string.app_apk_size, context.getString(R.string.app_size_unavailable)))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.app_update_apk_size, Formatter.formatFileSize(context, 1_000_000)))
            .assertIsDisplayed()
    }
}
