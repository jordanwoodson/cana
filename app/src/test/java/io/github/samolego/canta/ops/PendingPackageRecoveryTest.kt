package io.github.samolego.canta.ops

import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.util.InstallerCompletion
import io.github.samolego.canta.util.PackageInstallerResult.Result
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class PendingPackageRecoveryTest {
    private fun removal(action: String = "uninstall") = OperationRecord.newBuilder().setId("pending").setAction(action)
        .setPackageName("test.app").setUserId(10).setPreviousState("{\"installed\":true,\"updatedSystemApp\":true}").build()

    @Test fun delayedCallbackCanSettlePreviouslyUnknownRequest() {
        val completion = InstallerCompletion("request")
        val timedOut = completion.await(0, TimeUnit.MILLISECONDS)
        assertTrue(timedOut.outcomeUnknown)
        assertEquals("request", timedOut.requestId)
        assertNull(PendingPackageRecovery.settle(removal(), "{\"installed\":false}", null, awaiting = true))
        completion.complete(Result(true, null))
        assertFalse(completion.await(0, TimeUnit.MILLISECONDS).outcomeUnknown)
        assertEquals(true, PendingPackageRecovery.settle(removal(), "{\"installed\":false}", completion.terminalResult(), false))
    }
    @Test fun lostCallbackAndUnchangedStateRemainPendingAfterRestart() {
        assertNull(PendingPackageRecovery.settle(removal(), "{\"installed\":true}", null, false))
        assertEquals(true, PendingPackageRecovery.settle(removal(), "{\"installed\":false}", null, false))
    }
    @Test fun completedResetCannotMasqueradeAsCompletedUninstall() {
        assertEquals(false, PendingPackageRecovery.settle(removal(), "{\"installed\":true,\"updatedSystemApp\":false}", Result(true, null), false))
    }
    @Test fun terminalFailureIsSettledEvenWhenStateIsUnchanged() {
        assertEquals(false, PendingPackageRecovery.settle(removal(), "{\"installed\":true}", Result(false, "Denied"), false))
    }
    @Test fun disappearingPackageCannotCountAsSuccessfullyUnsuspended() {
        val pending = removal("unsuspend").toBuilder().setIntendedState("{\"suspended\":false}").build()
        val gone = "{\"installed\":false,\"suspended\":false}"
        assertNull(PendingPackageRecovery.settle(pending, gone, null, false))
        assertEquals(false, PendingPackageRecovery.settle(pending, gone, Result(true, null), false))
        assertNull(PendingPackageRecovery.settle(pending.toBuilder().clearIntendedState().build(), gone, null, false))
    }
    @Test fun preflightFailureCanBeClosedWithoutClaimingSuccess() {
        val failed = removal().toBuilder().setPreviousState("{}").build()
        assertEquals(false, PendingPackageRecovery.settle(failed, "{\"installed\":false}", null, false))
    }
    @Test fun undoReconcilesItsCapturedGoalInsteadOfOriginalActionVerb() {
        val undo = removal("enable").toBuilder().setUndoOf("original")
            .setPreviousState("{\"enabledSetting\":1}").setIntendedState("{\"enabledSetting\":3}").build()
        assertEquals(true, PendingPackageRecovery.settle(undo, "{\"enabledSetting\":3}", null, false))
    }
    @Test fun componentRecoveryChecksBothTargetAndEnabledSetting() {
        val component = removal("component").toBuilder()
            .setIntendedState("{\"component\":\"test.app/.Tracker\",\"enabledSetting\":2}").build()
        assertEquals(true, PendingPackageRecovery.settle(component, "{\"installed\":true,\"component\":\"test.app/.Tracker\",\"enabledSetting\":2}", null, false))
        assertNull(PendingPackageRecovery.settle(component, "{\"installed\":true,\"component\":\"test.app/.Other\",\"enabledSetting\":2}", null, false))
    }
    @Test fun preflightSettlementDoesNotCreateARecoveryObligation() {
        val record = removal().toBuilder().setPreviousState("{}").build()
        assertFalse(PendingPackageRecovery.finish(record, "{\"installed\":false}", false, "Preflight failed").changed)
    }
    @Test fun settlingUndoPreservesManualDataRecoveryWarning() {
        val original = removal().toBuilder().setPreviousState("{\"installed\":true}").build()
        val undo = removal().toBuilder().setId("undo").setUndoOf(original.id).setPreviousState("{\"installed\":false}").build()
        val result = PendingPackageRecovery.finish(undo, "{\"installed\":true}", true, "Applied", original)
        assertFalse(result.success)
        assertTrue(result.recoveryComplete)
        assertTrue(result.message.contains("deleted"))
    }
}
