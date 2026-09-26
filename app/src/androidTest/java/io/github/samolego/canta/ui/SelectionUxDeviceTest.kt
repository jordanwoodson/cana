package io.github.samolego.canta.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.data.SavedView
import io.github.samolego.canta.extension.add
import io.github.samolego.canta.ui.component.SelectionActionBar
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.AppSort
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.util.apps.Filter
import io.github.samolego.canta.util.apps.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** No package mutations: the test deliberately selects an unavailable target. */
class SelectionUxDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun profileScopedCollectionClearsOnSwitchWithoutRetargetingPendingAction() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.Main) {
            val model = AppListViewModel { _, _ -> JSONObject() }
            val originalUser = model.selectedUserId
            val target = "example.unavailable"
            val scoped = SavedView("scoped", "This profile only", originalUser, "carrier",
                setOf("category_carrier"), true, AppSort.SIZE.name, setOf(target))
            model.applySavedView(scoped)
            model.selectedApps.add(target)
            model.requestAction(PackageAction.DISABLE)
            val pending = model.pendingAction

            // The inventory may be unavailable; changing the view must still respect its scope.
            val other = UserProfile(originalUser + 10, "Other profile", UserProfile.Kind.WORK)
            model.selectUser(other, context.packageManager, context)

            assertEquals(other.id, model.selectedUserId)
            assertTrue(model.collectionPackages.isEmpty())
            assertTrue(model.activeFilterIds.isEmpty())
            assertEquals("", model.searchQuery)
            assertFalse(model.onlySystem)
            assertEquals(AppSort.NAME, model.sortOrder)
            assertTrue(model.selectedApps.isEmpty())
            assertSame(pending, model.pendingAction)
            assertEquals(originalUser, model.pendingAction!!.userId)
            assertEquals(listOf(target), model.pendingAction!!.requestedPackages)
        }
    }

    @Test fun globalCollectionRemainsAppliedAfterProfileSwitch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.Main) {
            val model = AppListViewModel { _, _ -> JSONObject() }
            val view = SavedView("global", "Every profile", null, "carrier",
                setOf("category_carrier"), true, AppSort.SIZE.name, setOf("example.unavailable"))
            model.applySavedView(view)

            model.selectUser(UserProfile(model.selectedUserId + 10, "Other profile", UserProfile.Kind.WORK),
                context.packageManager, context)

            assertEquals(view.packages, model.collectionPackages)
            assertEquals(view.filterIds, model.activeFilterIds)
            assertEquals(view.query, model.searchQuery)
            assertTrue(model.onlySystem)
            assertEquals(AppSort.SIZE, model.sortOrder)
        }
    }

    @Test fun unavailableUsageFilterDoesNotEraseOtherFilters() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.Main) {
            val model = AppListViewModel { _, _ -> JSONObject() }
            assertFalse(model.usageAvailable)
            model.applySavedView(SavedView("usage", "Unused carrier apps", null, "",
                setOf(Filter.unused.id, "category_carrier"), false, AppSort.LAST_USED.name))
            assertEquals(setOf("category_carrier"), model.activeFilterIds)
            assertEquals(AppSort.NAME, model.sortOrder)

            // A previously supported filter must also normalize when profile access changes.
            model.activeFilterIds = setOf(Filter.unused.id, "category_carrier")
            model.selectUser(UserProfile(model.selectedUserId + 10, "Other profile", UserProfile.Kind.WORK),
                context.packageManager, context)
            assertEquals(setOf("category_carrier"), model.activeFilterIds)
        }
    }

    @Test fun selectionRemainsReviewableWhenEveryTargetIsHidden() {
        val model = AppListViewModel()
        model.selectedApps.add("example.unavailable")
        model.searchQuery = "no matches"
        compose.setContent { CantaTheme { SelectionActionBar(model, AppsType.INSTALLED) } }
        compose.onNodeWithText("1 selected · 1 hidden by this view").assertIsDisplayed()
        compose.onNodeWithText("Review selection").performClick()
        compose.onNodeWithText("example.unavailable").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Clear selection").performClick()
        compose.runOnIdle { assertTrue(model.selectedApps.isEmpty()) }
        compose.onNodeWithText("1 selected · 1 hidden by this view").assertDoesNotExist()
    }

    @Test fun actionRequestRetainsUnavailableSelectedTargetsForPreflight() {
        val model = AppListViewModel()
        model.selectedApps.add("example.unavailable")
        compose.setContent { CantaTheme { SelectionActionBar(model, AppsType.INSTALLED) } }
        compose.onNodeWithText("Review actions").performClick()
        compose.onNodeWithText("Disable").performClick()
        compose.runOnIdle {
            assertEquals(listOf("example.unavailable"), model.pendingAction!!.requestedPackages)
            assertEquals(model.selectedUserId, model.pendingAction!!.userId)
            assertTrue(model.pendingAction!!.apps.isEmpty())
        }
    }
}
