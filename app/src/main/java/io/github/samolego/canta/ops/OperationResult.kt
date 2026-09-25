package io.github.samolego.canta.ops

data class OperationResult(val success: Boolean, val message: String, val changed: Boolean = false)

data class BatchResult(val results: List<OperationResult>) {
    val successCount: Int get() = results.count { it.success }
    val failureCount: Int get() = results.size - successCount
}
