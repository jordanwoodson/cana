package io.github.samolego.canta.ops

enum class BatchFeedbackPresentation { SUCCESS_SNACKBAR, FAILURE_DIALOG, VERIFICATION_DIALOG }

data class BatchFeedbackDecision(val presentation: BatchFeedbackPresentation, val canUndo: Boolean)

object BatchFeedbackPolicy {
    fun decide(batch: BatchResult, hasChangedRecords: Boolean): BatchFeedbackDecision = BatchFeedbackDecision(
        presentation = when {
            batch.failureCount > 0 -> BatchFeedbackPresentation.FAILURE_DIALOG
            batch.unknownCount > 0 -> BatchFeedbackPresentation.VERIFICATION_DIALOG
            else -> BatchFeedbackPresentation.SUCCESS_SNACKBAR
        },
        canUndo = batch.batchId != null && hasChangedRecords,
    )
}
