package io.github.samolego.canta.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.samolego.canta.ui.component.CantaTopBar
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import org.junit.Assert.assertEquals
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
}
