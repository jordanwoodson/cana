package io.github.samolego.canta.ops

import org.junit.Assert.*
import org.junit.Test

class SelectionPolicyTest {
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
}
