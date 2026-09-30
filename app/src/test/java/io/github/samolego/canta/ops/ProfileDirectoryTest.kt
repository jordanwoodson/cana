package io.github.samolego.canta.ops

import io.github.samolego.canta.util.apps.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDirectoryTest {
    private val personal = UserProfile(0, "Owner", UserProfile.Kind.PERSONAL)
    private val work = UserProfile(10, "Work", UserProfile.Kind.WORK)

    @Test fun refreshKeepsLastGoodProfilesWhenShizukuFails() = runBlocking {
        var shouldFail = false
        val directory = ProfileDirectory(load = {
            if (shouldFail) error("Shizuku disconnected")
            listOf(personal, work)
        }, onError = {})

        assertTrue(directory.refresh())
        assertEquals(work, directory.find(10))
        shouldFail = true
        assertFalse(directory.refresh())
        assertEquals(listOf(personal, work), directory.profiles.value)
    }
}
