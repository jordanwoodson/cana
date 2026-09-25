package io.github.samolego.canta.util

import org.junit.Assert.*
import org.junit.Test

class UninstallSequenceTest {
    @Test fun resetRemovesUpdatesBeforeUninstallingForUser() {
        val flags = mutableListOf<Int>()
        val result = UninstallSequence.execute(true, 4, { false }) {
            flags += it
            PackageInstallerResult.Result(true, null)
        }
        assertTrue(result.success)
        assertEquals(listOf(0, 4), flags)
    }

    @Test fun failedResetDoesNotContinueOrReportSuccess() {
        val flags = mutableListOf<Int>()
        val result = UninstallSequence.execute(true, 4, { false }) {
            flags += it
            PackageInstallerResult.Result(false, "Reset denied")
        }
        assertFalse(result.success)
        assertEquals("Reset denied", result.message)
        assertEquals(listOf(0), flags)
    }

    @Test fun successfulCallbackWithUpdateStillPresentIsFailure() {
        val flags = mutableListOf<Int>()
        val result = UninstallSequence.execute(true, 4, { true }) {
            flags += it
            PackageInstallerResult.Result(true, null)
        }
        assertFalse(result.success)
        assertEquals(listOf(0), flags)
    }

    @Test fun missingPackageAfterResetIsNotProofOfSuccess() {
        val result = UninstallSequence.execute(true, 4, { null }) {
            PackageInstallerResult.Result(true, null)
        }
        assertFalse(result.success)
    }

    @Test fun finalUninstallFailureIsPreserved() {
        val result = UninstallSequence.execute(true, 4, { false }) {
            PackageInstallerResult.Result(it == 0, if (it == 0) null else "User restricted")
        }
        assertFalse(result.success)
        assertEquals("User restricted", result.message)
    }

    @Test fun ordinaryUninstallDoesNotResetOrQueryUpdates() {
        val flags = mutableListOf<Int>()
        val result = UninstallSequence.execute(false, 0, { error("Unexpected query") }) {
            flags += it
            PackageInstallerResult.Result(true, null)
        }
        assertTrue(result.success)
        assertEquals(listOf(0), flags)
    }
}
