package io.github.samolego.canta.ops

import io.github.samolego.canta.util.PackageInstallerResult.Result
import io.github.samolego.canta.util.UninstallSequence
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class InstallerUncertaintyTest {
    @Test fun outstandingResetNeverStartsFallbackOrRepair() = runBlocking {
        val calls = mutableListOf<String>()
        val result = UpdateCleanupSequence.execute(UpdateState(true, false), { UpdateState(true, false) },
            reset = { calls += "reset"; Result(false, "Waiting", outcomeUnknown = true) },
            installExisting = { calls += "install"; Result(true, null) },
            uninstallForUser = { calls += "remove"; Result(true, null) })
        assertEquals(listOf("reset"), calls)
        assertTrue(result.outcomeUnknown)
    }
    @Test fun outstandingFallbackInstallationNeverStartsAnotherReset() = runBlocking {
        val calls = mutableListOf<String>()
        val result = UpdateCleanupSequence.execute(UpdateState(true, false), { UpdateState(true, true) },
            reset = { calls += "reset"; Result(false, "Denied") },
            installExisting = { calls += "install"; Result(false, "Waiting", outcomeUnknown = true) },
            uninstallForUser = { calls += "remove"; Result(true, null) })
        assertEquals(listOf("reset", "install"), calls)
        assertTrue(result.outcomeUnknown)
    }
    @Test fun unchangedSnapshotCannotCloseAnOutstandingInstallerOperation() = runBlocking {
        val state = "{\"installed\":true}"
        val result = packageOutcome(state, state, OperationResult(false, "Waiting", outcomeUnknown = true), "Unknown")
        assertTrue(result.changed)
        assertTrue(result.outcomeUnknown)
        assertFalse(result.success)
    }
    @Test fun outstandingUninstallDoesNotStartAnotherMutation() = runBlocking {
        var calls = 0
        val result = UninstallSequence.execute(false, 4, { false }) {
            calls++; Result(false, "Waiting", outcomeUnknown = true)
        }
        assertEquals(1, calls)
        assertTrue(result.outcomeUnknown)
    }
    @Test fun unreadablePostStateKeepsRecoveryPendingButPreflightFailureDoesNot() = runBlocking {
        assertTrue(packageOutcome("{\"installed\":true}", null, OperationResult(true, "Applied"), "Unknown").outcomeUnknown)
        assertFalse(packageOutcome("{}", null, OperationResult(false, "Preflight refused"), "Unknown").outcomeUnknown)
    }
}
