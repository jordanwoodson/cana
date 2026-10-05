package io.github.samolego.canta.ui

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.ui.component.CantaTopBar
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class SearchNavigationUiTest {
    @get:Rule val compose = createComposeRule()

    private fun showToolbar(model: AppListViewModel) {
        compose.setContent {
            CantaTheme {
                CantaTopBar({}, model, AppsType.INSTALLED, false, {}, {}, {},
                    MaterialTheme.colorScheme.primaryContainer, TopAppBarDefaults.enterAlwaysScrollBehavior())
            }
        }
    }

    @Test fun toolbarClosesSearchAndClearsTheActiveQuery() {
        val model = AppListViewModel()
        showToolbar(model)
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("calculator")
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.runOnIdle { assertEquals("", model.searchQuery) }
        compose.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test fun queryRestoredFromSavedViewCanBeClosedFromToolbar() {
        val model = AppListViewModel().apply { searchQuery = "carrier" }
        showToolbar(model)
        compose.onNode(hasSetTextAction()).assertTextContains("carrier")
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.runOnIdle { assertEquals("", model.searchQuery) }
    }

    @Test fun accessibilityCharacterNavigationExtendsTheQuerySelection() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val model = AppListViewModel()
        showToolbar(model)
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("calculator")
        compose.onNode(hasSetTextAction()).performTextInputSelection(TextRange(0))
        fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isEditable) return node
            for (index in 0 until node.childCount) findEditable(node.getChild(index))?.let { return it }
            return null
        }
        fun editableNode(): AccessibilityNodeInfo? = findEditable(automation.rootInActiveWindow)
        // Android publishes its accessibility tree asynchronously after Compose is idle.
        compose.waitUntil(10_000) {
            editableNode()?.let { it.text?.contains("calculator") == true &&
                it.textSelectionStart == 0 && it.textSelectionEnd == 0 } == true
        }
        val input = checkNotNull(editableNode())
        // Use the platform action sent by screen readers, not Compose's direct SetSelection.
        assertTrue("Extend selection by one character", input.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER)
            putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, true)
        }))
        compose.waitForIdle()
        assertEquals(TextRange(0, 1), compose.onNode(hasSetTextAction()).fetchSemanticsNode()
            .config[SemanticsProperties.TextSelectionRange])
        compose.runOnIdle { assertEquals("calculator", model.searchQuery) }
    }
}
