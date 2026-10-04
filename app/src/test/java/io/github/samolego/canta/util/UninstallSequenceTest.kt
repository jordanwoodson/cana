package io.github.samolego.canta.util

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class UninstallSequenceTest {
    @Test fun ordinaryRemovalAlwaysRequestsPerUserSystemAppSemantics() = runBlocking {
        for (requested in listOf(0, 4)) {
            val flags = mutableListOf<Int>()
            val result = UninstallSequence.execute(false, requested, { error("Unexpected update query") }) {
                flags += it
                PackageInstallerResult.Result(true, null)
            }
            assertTrue(result.success)
            assertEquals(listOf(4), flags)
        }
    }

    @Test fun keptDataRemovalStillCannotDowngradeTheSharedApk() = runBlocking {
        val flags = mutableListOf<Int>()
        val result = UninstallSequence.execute(false, 1, { error("Unexpected update query") }) {
            flags += it
            PackageInstallerResult.Result(true, null)
        }
        assertTrue(result.success)
        assertEquals(listOf(5), flags)
    }

    @Test fun factoryResetIsRefusedBeforeAnyMutation() = runBlocking {
        val result = UninstallSequence.execute(true, 4, { false }) {
            fail("Profile removal must never reset a shared APK")
            PackageInstallerResult.Result(true, null)
        }
        assertFalse(result.success)
    }

    @Test fun allUsersAndUnknownFlagsAreRefusedBeforeAnyMutation() = runBlocking {
        for (flags in listOf(2, 6, 8, -1)) {
            val result = UninstallSequence.execute(false, flags, { false }) {
                fail("Unsafe delete flags must never reach Android")
                PackageInstallerResult.Result(true, null)
            }
            assertFalse(result.success)
        }
    }

    @Test fun installerFailureAndUncertaintyArePreserved() = runBlocking {
        val failure = PackageInstallerResult.Result(false, "Still pending", outcomeUnknown = true, requestId = "request")
        assertEquals(failure, UninstallSequence.execute(false, 4, { false }) { failure })
    }
}
