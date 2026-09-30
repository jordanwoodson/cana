package io.github.samolego.canta.ops

import io.github.samolego.canta.util.apps.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNamingTest {
    private val labels = ProfileNaming.Labels(
        personal = "Personal", work = "Work profile", clone = "Clone profile",
        privateProfile = "Private space", user = "User", other = "Profile",
        fallbackUser = { "User $it" },
    )

    @Test fun missingOwnerEntryUsesPersonalName() {
        assertEquals("Personal", ProfileNaming.label(null, null, 0, 0, labels))
    }

    @Test fun missingOtherEntryUsesNumberedFallback() {
        assertEquals("User 10", ProfileNaming.label(null, null, 10, 0, labels))
        assertEquals("User 0", ProfileNaming.label(null, null, 0, 10, labels))
    }

    @Test fun knownKindWinsOverTechnicalDeviceName() {
        assertEquals("Personal", ProfileNaming.label(UserProfile.Kind.PERSONAL, "Owner", 0, 0, labels))
        assertEquals("Work profile", ProfileNaming.label(UserProfile.Kind.WORK, "Company", 10, 0, labels))
    }

    @Test fun NamedGenericProfileUsesItsName() {
        assertEquals("Guest", ProfileNaming.label(UserProfile.Kind.USER, "Guest", 11, 0, labels))
        assertEquals("Profile", ProfileNaming.label(UserProfile.Kind.OTHER, "  ", 12, 0, labels))
    }
}
