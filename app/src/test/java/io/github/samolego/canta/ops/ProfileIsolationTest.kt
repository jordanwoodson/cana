package io.github.samolego.canta.ops

import io.github.samolego.canta.util.apps.UserProfile
import org.junit.Assert.*
import org.junit.Test

class ProfileIsolationTest {
    private val personal = UserProfile(0, "Personal", UserProfile.Kind.PERSONAL)
    private val work = UserProfile(10, "Work", UserProfile.Kind.WORK, parentId = 0)

    @Test fun normalWorkRemovalAndParentRemovalWithIndependentWorkAreAllowed() {
        val profiles = listOf(personal, work.copy(deleteAppWithParent = false))
        assertTrue(ProfileIsolation.blockingProfiles(0, profiles, setOf(0, 10)).isEmpty())
        assertTrue(ProfileIsolation.blockingProfiles(10, profiles, setOf(0, 10)).isEmpty())
    }

    @Test fun cascadingOrUnknownChildPolicyRefusesParentRemovalOnlyWhenInstalled() {
        for (cascade in listOf(true, null)) {
            val profiles = listOf(personal, work.copy(deleteAppWithParent = cascade))
            assertEquals(listOf(10), ProfileIsolation.blockingProfiles(0, profiles, setOf(0, 10)).map { it.id })
            assertTrue(ProfileIsolation.blockingProfiles(10, profiles, setOf(0, 10)).isEmpty())
            assertTrue(ProfileIsolation.blockingProfiles(0, profiles, setOf(0)).isEmpty())
        }
    }

    @Test fun unrelatedParentAndNonzeroFullUserAreNotConfusedWithOwner() {
        val profiles = listOf(personal, UserProfile(11, "User", UserProfile.Kind.USER),
            work.copy(parentId = 11, deleteAppWithParent = true))
        assertTrue(ProfileIsolation.blockingProfiles(0, profiles, setOf(0, 10, 11)).isEmpty())
        assertEquals(listOf(10), ProfileIsolation.blockingProfiles(11, profiles, setOf(0, 10, 11)).map { it.id })
    }

    @Test fun propertiesAreParsedPerUserAndUnknownOrAmbiguousOutputFailsClosed() {
        val dump = """
Users:
  UserInfo{0:Owner:c13} serialNo=0 isPrimary=true
    UserProperties:
        mDeleteAppWithParent=false
  UserInfo{10:Work:1030} serialNo=10 isPrimary=false parentId=0
    UserProperties:
        mDeleteAppWithParent=true
  UserInfo{11:Private:1090} serialNo=11 isPrimary=false parentId=0
User types:
    mDeleteAppWithParent=false
"""
        assertEquals(mapOf(0 to false, 10 to true), ProfileIsolation.parseDeleteWithParent(dump))
        assertTrue(ProfileIsolation.parseDeleteWithParent("Permission denied").isEmpty())
        assertFalse(ProfileIsolation.parseDeleteWithParent(dump + dump).containsKey(10))
    }

    @Test fun missingTargetIsNeverTreatedAsASafeProfile() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileIsolation.blockingProfiles(99, listOf(personal, work), emptySet())
        }
    }

    @Test fun cleanupRefusesSharedApkEvenWithSelectedProfileRemoved() {
        assertFalse(ProfileIsolation.canCleanUpdates(0, mapOf(0 to false, 10 to true)))
        assertFalse(ProfileIsolation.canCleanUpdates(10, mapOf(0 to true, 10 to false)))
        assertFalse(ProfileIsolation.canCleanUpdates(0, mapOf(0 to true, 10 to false)))
        assertFalse(ProfileIsolation.canCleanUpdates(0, emptyMap()))
        assertTrue(ProfileIsolation.canCleanUpdates(0, mapOf(0 to false, 10 to false)))
    }
}
