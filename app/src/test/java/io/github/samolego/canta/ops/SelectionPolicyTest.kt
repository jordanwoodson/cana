package io.github.samolego.canta.ops

import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.apps.AppInfo
import org.junit.Assert.*
import org.junit.Test

class SelectionPolicyTest {
    private fun app(id: String, disabled: Boolean = false, suspended: Boolean = false,
                    removed: Boolean = false, updated: Boolean = false,
                    risk: RemovalRecommendation? = null) =
        AppInfo(id, id, "1", 1, true, removed, disabled, BloatData(null, null, risk),
            isUpdatedSystemApp = updated, isSuspended = suspended)

    @Test fun filteringNeverChangesTotalSelection() {
        val selection = SelectionSummary.of(setOf("recommended", "expert"), setOf("expert"))
        assertEquals(2, selection.total)
        assertEquals(1, selection.hidden)
        assertEquals(SelectionCheck.ALL, selection.visibleCheck)
    }
    @Test fun partialSelectionAndAnEmptyViewAreDistinct() {
        assertEquals(SelectionCheck.PARTIAL, SelectionSummary.of(setOf("a"), setOf("a", "b")).visibleCheck)
        val hidden = SelectionSummary.of(setOf("a"), emptySet())
        assertEquals(SelectionCheck.NONE, hidden.visibleCheck)
        assertEquals(1, hidden.hidden)
    }
    @Test fun deselectVisiblePreservesHiddenSelection() {
        assertEquals(setOf("hidden"), SelectionSummary.toggleVisible(setOf("hidden", "visible"), setOf("visible")))
        assertEquals(setOf("hidden", "one", "two"), SelectionSummary.toggleVisible(setOf("hidden", "one"), setOf("one", "two")))
    }

    @Test fun mixedInstalledStatesOfferOnlyUsefulReversibleActions() {
        val choices = ActionChoices.forSelection(listOf(app("enabled"), app("disabled", disabled = true, suspended = true)), false)
        assertEquals(PackageAction.UNINSTALL, choices.primary)
        assertEquals(listOf(PackageAction.DISABLE, PackageAction.ENABLE, PackageAction.SUSPEND, PackageAction.UNSUSPEND), choices.reversible)
        assertEquals(listOf(PackageAction.UNINSTALL_KEEP_DATA), choices.removing)
    }

    @Test fun allDisabledMakesEnablePrimary() {
        val choices = ActionChoices.forSelection(listOf(app("one", disabled = true), app("two", disabled = true)), false)
        assertEquals(PackageAction.ENABLE, choices.primary)
        assertEquals(listOf(PackageAction.SUSPEND), choices.reversible)
        assertTrue(PackageAction.UNINSTALL in choices.removing)
    }

    @Test fun expertOrUnsafeMakesDisablePrimary() {
        for (risk in listOf(RemovalRecommendation.EXPERT, RemovalRecommendation.UNSAFE)) {
            val choices = ActionChoices.forSelection(listOf(app("risk", risk = risk)), false)
            assertEquals(PackageAction.DISABLE, choices.primary)
            assertFalse(PackageAction.DISABLE in choices.reversible)
        }
    }

    @Test fun removedTabOffersUpdateRemovalOnlyWhenRelevant() {
        val plain = ActionChoices.forSelection(listOf(app("plain", removed = true)), true)
        assertEquals(PackageAction.REINSTALL, plain.primary)
        assertTrue(plain.reversible.isEmpty())
        assertTrue(plain.removing.isEmpty())
        val updated = ActionChoices.forSelection(listOf(app("updated", removed = true, updated = true)), true)
        assertEquals(listOf(PackageAction.REMOVE_UPDATES), updated.removing)
    }

    @Test fun emptySelectionOffersNoSecondaryActions() {
        val installed = ActionChoices.forSelection(emptyList(), false)
        val removed = ActionChoices.forSelection(emptyList(), true)
        assertTrue(installed.reversible.isEmpty() && installed.removing.isEmpty())
        assertTrue(removed.reversible.isEmpty() && removed.removing.isEmpty())
    }
}
