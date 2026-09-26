package io.github.samolego.canta.ops

/** An unreadable post-state is not proof that the mutation did nothing. */
internal fun packageOutcome(before: String, after: String?, outcome: OperationResult, unverified: String): OperationResult {
    val unknown = outcome.outcomeUnknown || after == null || after == "{}"
    return outcome.copy(
        success = outcome.success && !unknown,
        message = if (unknown && before != "{}") unverified + "\n" + outcome.message else outcome.message,
        changed = before != "{}" && (unknown || !SnapshotState.equal(before, after!!)),
        recoveryComplete = outcome.recoveryComplete && !unknown,
        outcomeUnknown = outcome.outcomeUnknown || unknown && before != "{}",
    )
}
