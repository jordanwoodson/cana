package io.github.samolego.canta.ops

import org.junit.Assert.*
import org.junit.Test

class AppTargetTest {
    @Test fun settingsCommandAlwaysCarriesTargetProfile() {
        assertEquals(listOf("am", "start", "--user", "10", "-a", "android.settings.APPLICATION_DETAILS_SETTINGS", "-d", "package:com.example.app"),
            AppTarget("com.example.app", 10).settingsCommand())
    }
    @Test fun rejectsInvalidProfilesAndPackages() {
        assertThrows(IllegalArgumentException::class.java) { AppTarget("com.example;id", 10) }
        assertThrows(IllegalArgumentException::class.java) { AppTarget("com.example.app", -1) }
    }
}
