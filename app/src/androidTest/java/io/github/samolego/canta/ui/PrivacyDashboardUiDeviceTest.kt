package io.github.samolego.canta.ui

import android.os.Build
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ui.screen.PrivacyDashboardPage
import io.github.samolego.canta.ui.theme.CantaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import rikka.shizuku.Shizuku

class PrivacyDashboardUiDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun newRestrictionInTheSameProfileShowsItsAppName() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        val services = CanaServices.getInstance()
        val first = "com.android.adservices.api"
        val second = "com.android.egg"
        val originals = services.desiredPrivacy.state.first().networkBlocksList
            .filter { it.userId == 0 && it.packageName in setOf(first, second) }
        try {
            services.desiredPrivacy.setBlock(first, 0, 0, true)
            compose.setContent { CantaTheme {
                CompositionLocalProvider(LocalCanaSnackbar provides SnackbarHostState()) {
                    PrivacyDashboardPage {}
                }
            } }
            compose.waitUntil(90_000) {
                compose.onAllNodesWithText("Ad Privacy").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Ad Privacy").assertIsDisplayed()

            services.desiredPrivacy.setBlock(second, 0, 0, true)
            compose.waitUntil(90_000) {
                compose.onAllNodesWithText("com.android.egg").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Android Easter Egg").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Android Easter Egg").assertIsDisplayed()
        } finally {
            services.desiredPrivacy.forget(first, 0, false)
            services.desiredPrivacy.forget(second, 0, false)
            originals.forEach { services.desiredPrivacy.saveReviewed(it, false) }
        }
        Unit
    }
}
