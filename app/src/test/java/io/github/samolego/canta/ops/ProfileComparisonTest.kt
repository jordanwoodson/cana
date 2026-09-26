package io.github.samolego.canta.ops

import org.junit.Assert.*
import org.junit.Test

class ProfileComparisonTest {
    @Test fun unavailableProfileIsNeverReportedAsAbsentAndRowsRetainUserIdentity() {
        val rows = ProfileComparison.rows(listOf(
            ComparisonProfileInventory(0, listOf(ComparisonPackage("only.personal", "Personal app"))),
            ComparisonProfileInventory(10, emptyList()),
            ComparisonProfileInventory(11, null),
        ))
        assertEquals(1, rows.size)
        assertEquals(listOf(0, 10, 11), rows.single().cells.map { it.userId })
        assertEquals(listOf(ComparisonPackageStatus.INSTALLED, ComparisonPackageStatus.ABSENT, ComparisonPackageStatus.UNAVAILABLE),
            rows.single().cells.map { it.status })
    }

    @Test fun removedIsDifferentFromAbsentAndDoesNotShowStaleEnabledFlags() {
        val rows = ProfileComparison.rows(listOf(ComparisonProfileInventory(10,
            listOf(ComparisonPackage("removed.app", "Removed", removed = true, disabled = true, suspended = true))),
            ComparisonProfileInventory(0, emptyList())))
        val removed = rows.single().cells[0]
        assertEquals(ComparisonPackageStatus.REMOVED, removed.status)
        assertFalse(removed.disabled)
        assertFalse(removed.suspended)
        assertEquals(ComparisonPackageStatus.ABSENT, rows.single().cells[1].status)
    }

    @Test fun disabledAndSuspendedBothRemainVisibleForInstalledApps() {
        val cell = ProfileComparison.rows(listOf(ComparisonProfileInventory(0,
            listOf(ComparisonPackage("limited.app", "Limited", disabled = true, suspended = true)))))
            .single().cells.single()
        assertEquals(ComparisonPackageStatus.INSTALLED, cell.status)
        assertTrue(cell.disabled)
        assertTrue(cell.suspended)
    }

    @Test fun savedPrivacyIsProfileScopedAndRemainsVisibleWhenInventoryIsUnavailable() {
        val rows = ProfileComparison.rows(listOf(
            ComparisonProfileInventory(0, null, mapOf("saved.app" to ComparisonSavedPrivacy(blockNetwork = true))),
            ComparisonProfileInventory(10, emptyList(), mapOf("saved.app" to ComparisonSavedPrivacy(denyMetered = true))),
            ComparisonProfileInventory(11, emptyList(), emptyMap()),
            ComparisonProfileInventory(12, emptyList(), null),
        ))
        assertEquals("saved.app", rows.single().packageName)
        val cells = rows.single().cells
        assertEquals(ComparisonPackageStatus.UNAVAILABLE, cells[0].status)
        assertEquals(ComparisonSavedPrivacy(blockNetwork = true), cells[0].savedPrivacy)
        assertEquals(ComparisonSavedPrivacy(denyMetered = true), cells[1].savedPrivacy)
        assertEquals(ComparisonSavedPrivacy(), cells[2].savedPrivacy)
        assertNull(cells[3].savedPrivacy)
    }

    @Test fun joinUsesPackageIdentityAndSearchMatchesLabelsFromAnyProfile() {
        val profiles = listOf(
            ComparisonProfileInventory(0, listOf(ComparisonPackage("same.app", "First label"))),
            ComparisonProfileInventory(10, listOf(ComparisonPackage("same.app", "Work label"), ComparisonPackage("other.app", "Other"))),
        )
        assertEquals(2, ProfileComparison.rows(profiles).size)
        assertEquals("same.app", ProfileComparison.rows(profiles, " WORK LABEL ").single().packageName)
        assertEquals("same.app", ProfileComparison.rows(profiles, "SAME.APP").single().packageName)
        assertTrue(ProfileComparison.rows(profiles, "does not exist").isEmpty())
    }
}
