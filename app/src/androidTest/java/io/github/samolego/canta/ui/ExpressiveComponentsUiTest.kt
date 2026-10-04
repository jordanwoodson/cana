package io.github.samolego.canta.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.component.CanaBanner
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.TrackerScanControls
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@SdkSuppress(minSdkVersion = 29)
class ExpressiveComponentsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bannerKeepsMessageReadableAndActionsReachableAtLargeText() {
        var clicks = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                CantaTheme {
                    Column(Modifier.width(320.dp)) {
                        CanaBanner(Icons.Default.Info, "Recommendations need a refresh",
                            modifier = Modifier.testTag("banner")) {
                            TextButton(onClick = { clicks++ }) { Text("Review recommendations") }
                            TextButton(onClick = { clicks++ }) { Text("Try again") }
                        }
                    }
                }
            }
        }
        capture("banner-320dp-font2.0")
        val banner = compose.onNodeWithTag("banner").fetchSemanticsNode().boundsInRoot
        val messageNode = compose.onNodeWithText("Recommendations need a refresh").assertIsDisplayed()
        assertTextNotClipped(messageNode)
        val message = messageNode.fetchSemanticsNode().boundsInRoot
        assertTrue("Message should use most of the banner width", message.width >= banner.width * .7f)
        listOf("Review recommendations", "Try again").forEach { label ->
            val node = compose.onNodeWithText(label).assertIsDisplayed()
            assertTextNotClipped(node)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("Action stays within banner", bounds.left >= banner.left && bounds.right <= banner.right)
            assertTrue("Actions follow the message", bounds.top >= message.bottom)
            node.performClick()
        }
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    private fun assertTextNotClipped(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Text layout was not available", layouts.isNotEmpty())
        // FlowRow's intrinsic pass can retain a paragraph wider than the final
        // wrap-content Text. Check drawn lines, not unused paragraph space.
        assertTrue("Text is clipped: ${layouts.map { it.layoutInput.text }}", layouts.all { layout ->
            !layout.didOverflowHeight && (0 until layout.lineCount).all { line ->
                !layout.isLineEllipsized(line) && layout.getLineLeft(line) >= 0f &&
                    layout.getLineRight(line) <= layout.size.width
            }
        })
    }

    @Test fun trackerScanStatusAndActionRemainReadableAtLargeText() {
        val model = AppListViewModel()
        try {
            compose.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                    CantaTheme {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Column(Modifier.width(320.dp)) { TrackerScanControls(model) }
                        }
                    }
                }
            }
            val status = compose.onNode(hasText("installed apps scanned", substring = true)).assertIsDisplayed()
            assertTextNotClipped(status)
            val action = compose.onNodeWithText(
                InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.tracker_scan_all),
            ).assertIsDisplayed()
            assertTextNotClipped(action)
            assertTrue("The scan action should follow its readable status",
                action.fetchSemanticsNode().boundsInRoot.top >= status.fetchSemanticsNode().boundsInRoot.bottom)
            capture("tracker-status-320dp-font2.0")
        } finally {
            model.viewModelScope.cancel()
        }
    }

    @Test fun secondaryHeaderWrapsAndBackRemainsReachableInRtlAtLargeText() {
        var backs = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                CantaTheme {
                    Column(Modifier.width(320.dp)) {
                        ScreenTopBar(onNavigateBack = { backs++ },
                            title = { Text("Device settings") })
                    }
                }
            }
        }
        capture("header-rtl-320dp-font2.0")
        val title = compose.onNodeWithText("Device settings").assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertTextNotClipped(title)
        val back = compose.onNodeWithContentDescription(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.back),
        ).assertIsDisplayed()
        assertTrue("Back belongs on the leading RTL edge",
            back.fetchSemanticsNode().boundsInRoot.left >= title.fetchSemanticsNode().boundsInRoot.right)
        back.performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-components")
        check(folder.exists() || folder.mkdirs())
        // PixelCopy of the rendered Compose root avoids capturing a stale display
        // frame on software emulators after semantics have already settled.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            File(folder, "$name.png").outputStream().use {
                assertTrue("Could not save $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }
}
