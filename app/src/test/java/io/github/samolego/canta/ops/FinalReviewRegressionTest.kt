package io.github.samolego.canta.ops

import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.util.authenticatedAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FinalReviewRegressionTest {
    private fun record(id: String, action: String, before: String, after: String = "{}") = OperationRecord.newBuilder()
        .setId(id).setBatchId(id).setPackageName("test.app").setUserId(10).setAction(action)
        .setPreviousState(before).setAfterState(after).setCompleted(true).setChanged(true).build()

    @Test fun undoReinstallAndPartialCleanupKeepRetainedData() {
        for (action in listOf("reinstall", "remove_updates")) {
            val plan = RestoreScript.plan(record("r", action, "{\"installed\":false}", "{\"installed\":true}"))
            assertEquals(listOf("pm", "uninstall", "-k", "--user", "10", "test.app"), plan.commands.single())
        }
    }

    @Test fun unknownPostMutationStateFailsButRemainsInBothRecoveryPaths() {
        val before = "{\"enabledSetting\":0}"
        val result = packageOutcome(before, null, OperationResult(true, "applied"), "State unavailable")
        assertFalse(result.success)
        assertTrue(result.changed)
        val stored = record("disabled", "disable", before).toBuilder().setSuccess(result.success).setChanged(result.changed).build()
        assertEquals(listOf(stored), ManagementPolicy.lastBatch(listOf(stored)))
        assertTrue(RestoreScript.generate(listOf(stored)).contains("'default-state' '--user' '10'"))
        assertFalse(packageOutcome("{}", null, OperationResult(false, "preflight"), "State unavailable").changed)
    }

    @Test fun completedReversibleRecoveryDoesNotTrapEarlierBatchesBehindManualWork() {
        val disabled = record("disabled", "disable", "{\"enabledSetting\":0}")
        for (action in listOf("remove_updates", "uninstall")) {
            val removed = record("removed", action, "{\"installed\":true}")
            val recovered = record("recovery", action, "{}").toBuilder().setUndoOf("removed")
                .setSuccess(false).setRecoveryComplete(true).setResultMessage("Manual APK/data recovery remains").build()
            assertEquals(listOf(disabled), ManagementPolicy.lastBatch(listOf(disabled, removed, recovered)))
            assertEquals(listOf(removed), ManagementPolicy.lastBatch(listOf(disabled, removed,
                recovered.toBuilder().setRecoveryComplete(false).build())))
        }
    }

    @Test fun cancelledAuthenticationNeverRunsAnyCapturedAction() = runBlocking {
        for (path in listOf("preset", "ota", "history", "result-undo", "ordinary")) {
            var calls = 0
            var prompts = 0
            assertNull(authenticatedAction(true, { prompts++; false }) { calls++; path })
            assertEquals(path, 0, calls)
            assertEquals(1, prompts)
            assertEquals(path, authenticatedAction(true, { true }) { calls++; path })
            assertEquals(path, authenticatedAction(false, { error("Must not prompt") }) { calls++; path })
            assertEquals(2, calls)
        }
    }
}
