package io.github.samolego.canta.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchFeedbackPolicyTest {
    @Test fun successfulChangedBatchUsesSnackbarWithUndo() {
        val batch = BatchResult(listOf(OperationResult(true, "disabled", changed = true)), "batch-1")
        val decision = BatchFeedbackPolicy.decide(batch, hasChangedRecords = true)
        assertEquals(BatchFeedbackPresentation.SUCCESS_SNACKBAR, decision.presentation)
        assertTrue(decision.canUndo)
    }

    @Test fun successfulBatchWithoutChangedRecordsHasNoUndo() {
        val batch = BatchResult(listOf(OperationResult(true, "already disabled")), "batch-2")
        assertFalse(BatchFeedbackPolicy.decide(batch, hasChangedRecords = false).canUndo)
        assertFalse(BatchFeedbackPolicy.decide(batch.copy(batchId = null), hasChangedRecords = true).canUndo)
    }

    @Test fun failureAlwaysUsesDialogEvenWhenOtherItemsSucceeded() {
        val batch = BatchResult(listOf(OperationResult(true, "changed", changed = true), OperationResult(false, "failed")), "batch-3")
        val decision = BatchFeedbackPolicy.decide(batch, hasChangedRecords = true)
        assertEquals(BatchFeedbackPresentation.FAILURE_DIALOG, decision.presentation)
        assertTrue(decision.canUndo)
    }

    @Test fun unknownOutcomeRequiresVerificationDialog() {
        val batch = BatchResult(listOf(OperationResult(false, "waiting", outcomeUnknown = true)), "batch-4")
        assertEquals(BatchFeedbackPresentation.VERIFICATION_DIALOG,
            BatchFeedbackPolicy.decide(batch, hasChangedRecords = false).presentation)
    }
}
