package io.github.samolego.canta.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.samolego.canta.ui.menu.FiltersMenu
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryFilterDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allCategoriesAreReachableAndSelectionUpdatesTheMenu() {
        val model = AppListViewModel()
        compose.setContent { CantaTheme { FiltersMenu(true, {}, model) } }
        compose.onNodeWithText("Any").performClick()
        for (category in listOf("Google", "OEM", "Carrier", "AOSP", "Misc")) {
            compose.onNodeWithText(category).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("AOSP").performScrollTo().performClick()
        compose.onNodeWithText("AOSP").assertIsDisplayed()
        assertEquals("AOSP", model.selectedFilter.name)
    }
}
