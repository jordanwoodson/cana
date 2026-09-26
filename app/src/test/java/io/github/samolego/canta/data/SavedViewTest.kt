package io.github.samolego.canta.data

import org.junit.Assert.*
import org.junit.Test

class SavedViewTest {
    @Test fun namedProfileCollectionRoundTripsStableIdentifiers() {
        val view = SavedView("view-1", "Work carrier apps", 10, "carrier", setOf("category_carrier", "risk_recommended"), false, "SIZE", setOf("a", "b"))
        val restored = SavedViewJson.decode(SavedViewJson.encode(listOf(view))).single()
        assertEquals(view, restored)
        assertTrue(restored.appliesTo(10))
        assertFalse(restored.appliesTo(0))
    }
    @Test fun globalViewIsAvailableInEveryProfile() {
        assertTrue(SavedView("v", "All disabled", null, "", setOf("disabled"), false, "NAME").appliesTo(42))
    }
    @Test fun malformedStorageIsReportedInsteadOfSilentlyOverwritten() {
        assertThrows(IllegalArgumentException::class.java) { SavedViewJson.decode("not json") }
    }
}
