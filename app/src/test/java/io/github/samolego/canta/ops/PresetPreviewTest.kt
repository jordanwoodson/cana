package io.github.samolego.canta.ops

import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.LockdownSettings
import org.junit.Assert.*
import org.junit.Test

class PresetPreviewTest {
    private val original = CantaPresetData("Original", "", 1, setOf("old.app", "same.app"),
        uuid = "stable-id", lockdown = listOf(LockdownSettings("privacy.app", denyMetered = true)), profileKind = "WORK")

    @Test fun matchingIdentityShowsRemovalTargetsAndPrivacyChangesSeparately() {
        val imported = original.copy(name = "Updated", apps = setOf("new.app", "same.app"), lockdown = listOf(
            LockdownSettings("privacy.app", blockNetwork = true), LockdownSettings("new.privacy", revokePermissions = true)))
        val review = PresetPreview.reviewImport(imported, listOf(original), emptyList())
        assertEquals(original, review.previous)
        assertEquals(setOf("new.app"), review.diff.addedRemovals)
        assertEquals(setOf("old.app"), review.diff.removedRemovals)
        assertEquals(setOf("privacy.app", "new.privacy"), review.diff.privacyChanges.map { it.packageName }.toSet())
        val changed = review.diff.privacyChanges.single { it.packageName == "privacy.app" }
        assertEquals(LockdownSettings("privacy.app", denyMetered = true), changed.before)
        assertEquals(LockdownSettings("privacy.app", blockNetwork = true), changed.after)
        assertTrue(review.diff.profileKindChanged.not())
        assertEquals(original, review.previous) // Building a review never replaces the saved entry.
    }

    @Test fun removedPrivacyAndChangedProfileAreVisible() {
        val review = PresetPreview.reviewImport(original.copy(lockdown = emptyList(), profileKind = "PERSONAL"),
            listOf(original), emptyList())
        assertNull(review.diff.privacyChanges.single().after)
        assertTrue(review.diff.profileKindChanged)
    }

    @Test fun sameNameWithDifferentIdentityIsANewPreset() {
        val review = PresetPreview.reviewImport(original.copy(uuid = "new-id"), listOf(original), emptyList())
        assertNull(review.previous)
        assertEquals(setOf("old.app", "same.app"), review.diff.addedRemovals)
    }

    @Test fun missingPackagesAndAlreadyRemovedPackagesKeepTheirProfileIdentity() {
        val preview = PresetPreview.forProfiles(original, listOf(PresetProfileInventory(10, "Work", "WORK",
            installedPackages = setOf("same.app"), knownPackages = setOf("same.app", "old.app"))))
        assertEquals(setOf("privacy.app"), preview.single().missingPackages)
        assertEquals(setOf("old.app"), preview.single().alreadyRemovedPackages)
        assertFalse(preview.single().profileMismatch)
        assertEquals(10, preview.single().userId)
    }

    @Test fun unavailableInventoryCannotBeReportedAsMissingOrSuccessfullyChecked() {
        val preview = PresetPreview.forProfiles(original, listOf(PresetProfileInventory(0, "Personal", "PERSONAL"),
            PresetProfileInventory(11, "Unavailable", null)))
        assertTrue(preview[0].profileMismatch)
        assertFalse(preview[0].inventoryAvailable)
        assertTrue(preview[0].missingPackages.isEmpty())
        assertFalse(preview[1].profileMismatch)
        assertTrue(preview[1].profileKindUnknown)
    }

    @Test fun reviewCapturesCollectionsBeforeCallerChangesThem() {
        val packages = mutableSetOf("first.app")
        val privacy = mutableListOf(LockdownSettings("privacy.app", denyMetered = true))
        val review = PresetPreview.reviewImport(original.copy(apps = packages, lockdown = privacy), emptyList(), emptyList())
        packages += "unreviewed.app"
        privacy.clear()
        assertEquals(setOf("first.app"), review.preset.apps)
        assertEquals(1, review.preset.lockdown.size)
    }
}
