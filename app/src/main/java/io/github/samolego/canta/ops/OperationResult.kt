package io.github.samolego.canta.ops

data class OperationResult(
    val success: Boolean,
    val message: String,
    val changed: Boolean = false,
    val freedBytes: Long = 0,
    val skipped: Boolean = false,
)

data class BatchResult(val results: List<OperationResult>) {
    val successCount: Int get() = results.count { it.success && !it.skipped }
    val failureCount: Int get() = results.count { !it.success }
    val skippedCount: Int get() = results.count { it.skipped }
    val freedBytes: Long get() = results.sumOf { it.freedBytes }
}
