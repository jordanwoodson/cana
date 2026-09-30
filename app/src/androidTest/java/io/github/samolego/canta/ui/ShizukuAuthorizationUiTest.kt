package io.github.samolego.canta.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.dialog.ShizukuRequirementContent
import io.github.samolego.canta.util.shizuku.AuthorizationState.*
import io.github.samolego.canta.util.shizuku.ShizukuAuthorization
import io.github.samolego.canta.util.shizuku.ShizukuStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShizukuAuthorizationUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun disconnectedActionsAreDisabledAndInstallationRemainsAvailable() {
        var installs = 0
        var requests = 0
        compose.setContent { MaterialTheme {
            ShizukuRequirementContent(DISCONNECTED, ShizukuStatus.NOT_INSTALLED, false,
                onAuthorize = { requests++ }, onOpenManager = { requests++ }, onInstall = { installs++ },
                onOpenDeveloperSettings = {}, onClose = {})
        } }
        compose.onNodeWithContentDescription(text(R.string.start_shizuku_service)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.grant_shizuku_permission_to_canta)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.install_shizuku)).performClick()
        compose.runOnIdle { assertEquals(1, installs); assertEquals(0, requests) }
    }

    @Test fun requestProgressPreventsRepeatTapsAndDenialKeepsSetupOpenForRetry() {
        val requests = mutableListOf<Int>()
        var closes = 0
        val authorization = ShizukuAuthorization({ READY }, requests::add)
        authorization.refresh()
        compose.setContent { MaterialTheme {
            val state by authorization.state.collectAsState()
            ShizukuRequirementContent(state, ShizukuStatus.ACTIVE, false,
                onAuthorize = authorization::request, onOpenManager = {}, onInstall = {},
                onOpenDeveloperSettings = {}, onClose = { closes++ })
        } }
        val authorize = compose.onNodeWithContentDescription(text(R.string.grant_shizuku_permission_to_canta))
        authorize.performClick()
        authorize.assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.shizuku_waiting)).assertExists()
        compose.runOnIdle {
            assertEquals(1, requests.size)
            authorization.onResult(requests.single(), false)
        }
        compose.onNodeWithText(text(R.string.shizuku_denied)).assertExists()
        authorize.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, requests.size); assertEquals(0, closes) }
    }

    @Test fun blockedPermissionOffersManagerAndClose() {
        var managerOpens = 0
        var closes = 0
        compose.setContent { MaterialTheme {
            ShizukuRequirementContent(BLOCKED, ShizukuStatus.ACTIVE, false,
                onAuthorize = {}, onOpenManager = { managerOpens++ }, onInstall = {},
                onOpenDeveloperSettings = {}, onClose = { closes++ })
        } }
        compose.onNodeWithContentDescription(text(R.string.grant_shizuku_permission_to_canta)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.shizuku_open_manager)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.runOnIdle { assertEquals(1, managerOpens); assertEquals(1, closes) }
    }
}
