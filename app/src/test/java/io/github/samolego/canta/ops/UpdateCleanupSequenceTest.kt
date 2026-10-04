package io.github.samolego.canta.ops

import io.github.samolego.canta.util.PackageInstallerResult.Result
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class UpdateCleanupSequenceTest {
    @Test fun postResetVerificationFailureRepairsResurrectedAppWithoutInstallingAgain() = runBlocking {
        var state = UpdateState(true, false)
        val result = UpdateCleanupSequence.execute(state, { state },
            reset = { state = UpdateState(false, true); error("Peer read failed after callback") },
            installExisting = { fail("Verification failure must not trigger another install"); Result(false, null) },
            uninstallForUser = { state = state.copy(installed = false); Result(true, null) })
        assertFalse(result.success)
        assertFalse(state.installed)
    }
    @Test fun refusedResetNeverStartsTheInstallFallback() = runBlocking {
        var installed = false
        val result = UpdateCleanupSequence.execute(UpdateState(true, false), { UpdateState(true, installed) },
            reset = { throw PackageMutationRefused("Another profile started using the update") },
            installExisting = { installed = true; Result(true, null) },
            uninstallForUser = { Result(false, "Cascade blocked") })
        assertFalse(result.success)
        assertFalse("A preflight refusal must not cause another mutation", installed)
    }
    @Test fun directResetVerifiesUpdateRemovalAndKeepsAppUninstalled() = runBlocking {
        var state = UpdateState(true, false)
        val result = UpdateCleanupSequence.execute(state, { state },
            reset = { state = UpdateState(false, false); Result(true, null) },
            installExisting = { error("Not needed") }, uninstallForUser = { error("Not needed") })
        assertTrue(result.success)
    }

    @Test fun repairsAnAppResurrectedByReset() = runBlocking {
        var state = UpdateState(true, false)
        val result = UpdateCleanupSequence.execute(state, { state },
            reset = { state = UpdateState(false, true); Result(true, null) },
            installExisting = { error("Not needed") },
            uninstallForUser = { state = state.copy(installed = false); Result(true, null) })
        assertTrue(result.success)
        assertFalse(state.installed)
    }

    @Test fun failedDirectResetFallsBackToInstallResetAndRemove() = runBlocking {
        var state = UpdateState(true, false)
        val calls = mutableListOf<String>()
        var attempts = 0
        val result = UpdateCleanupSequence.execute(state, { state },
            reset = {
                calls += "reset"
                if (++attempts == 1) Result(false, "Direct reset denied")
                else { state = state.copy(updated = false); Result(true, null) }
            },
            installExisting = { calls += "install"; state = state.copy(installed = true); Result(true, null) },
            uninstallForUser = { calls += "remove"; state = state.copy(installed = false); Result(true, null) })
        assertTrue(result.message, result.success)
        assertEquals(listOf("reset", "install", "reset", "remove"), calls)
        assertEquals(UpdateState(false, false), state)
    }

    @Test fun failedFallbackStillRestoresOriginalUninstalledState() = runBlocking {
        var state = UpdateState(true, false)
        val result = UpdateCleanupSequence.execute(state, { state },
            reset = { Result(false, "Reset denied") },
            installExisting = { state = state.copy(installed = true); Result(true, null) },
            uninstallForUser = { state = state.copy(installed = false); Result(true, null) })
        assertFalse(result.success)
        assertEquals("Reset denied", result.message)
        assertFalse(state.installed)
    }

    @Test fun successfulCallbackWithoutRemovingUpdateIsFailure() = runBlocking {
        val state = UpdateState(true, false)
        val result = UpdateCleanupSequence.execute(state, { state },
            reset = { Result(true, null) }, installExisting = { error("Not needed") },
            uninstallForUser = { error("Not needed") })
        assertFalse(result.success)
    }

    @Test fun missingVerificationCannotBeSuccess() = runBlocking {
        val result = UpdateCleanupSequence.execute(UpdateState(true, false), { null },
            reset = { Result(true, null) }, installExisting = { error("Not needed") },
            uninstallForUser = { error("Not needed") })
        assertFalse(result.success)
    }

    @Test fun noUpdateDoesNotInvokePrivilegedActions() = runBlocking {
        val result = UpdateCleanupSequence.execute(UpdateState(false, false), { error("Not needed") },
            reset = { error("Not needed") }, installExisting = { error("Not needed") },
            uninstallForUser = { error("Not needed") })
        assertTrue(result.success)
    }

    @Test fun failureToRemoveResurrectedAppIsNotHidden() = runBlocking {
        val result = UpdateCleanupSequence.execute(UpdateState(true, false), { UpdateState(false, true) },
            reset = { Result(true, null) }, installExisting = { error("Not needed") },
            uninstallForUser = { Result(false, "User restricted") })
        assertFalse(result.success)
        assertEquals("User restricted", result.message)
    }

    @Test fun exceptionDuringFallbackStillRepairsSelectedProfile() = runBlocking {
        var state = UpdateState(true, false)
        var resets = 0
        val result = UpdateCleanupSequence.execute(state, { state },
            reset = { if (++resets == 1) Result(false, "Direct failed") else error("Binder died") },
            installExisting = { state = state.copy(installed = true); Result(true, null) },
            uninstallForUser = { state = state.copy(installed = false); Result(true, null) })
        assertFalse(result.success)
        assertFalse(state.installed)
    }
}
