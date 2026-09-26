package io.github.samolego.canta.ops

import io.github.samolego.canta.data.BatchStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class BatchItem(val key: String, val packageName: String, val userId: Int, val action: String)
data class BatchProgress(val id: String, val title: String, val completed: Int, val total: Int, val stopping: Boolean = false)

/** The caller observes work; its lifecycle never owns privileged execution. */
data class BatchMessages(
    val stopped: String = "Not started: batch stopped",
    val failed: String = "Operation failed",
    val storageFailed: String = "The batch could not be fully saved. Review History and app state before retrying.",
)

class BatchCoordinator(private val store: BatchStore, private val scope: CoroutineScope,
    private val messages: BatchMessages = BatchMessages(),
    private val onAttemptFinished: (BatchItem) -> Unit = {},
) {
    private val execution = Mutex()
    private val mutableActive = MutableStateFlow<BatchProgress?>(null)
    val active: StateFlow<BatchProgress?> = mutableActive.asStateFlow()
    private val ready = scope.async { store.markInterrupted() }
    suspend fun awaitReady() { ready.await() }

    suspend fun requestStop(id: String): Boolean = try {
        store.requestStop(id)
        mutableActive.update { if (it?.id == id) it.copy(stopping = true) else it }
        true
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { false }

    suspend fun run(title: String, items: List<BatchItem>, execute: suspend (BatchItem, String) -> OperationResult): BatchResult {
        val captured = items.toList()
        if (captured.isEmpty()) return BatchResult(emptyList())
        return scope.async {
            val id = UUID.randomUUID().toString()
            var persisted = false
            try {
            ready.await()
            store.append(id, title, captured)
            persisted = true
            execution.withLock {
                mutableActive.value = BatchProgress(id, title, 0, captured.size)
                try {
                    val results = captured.mapIndexed { index, item ->
                        val stopped = store.batches.first().first { it.id == id }.stopRequested
                        val result = if (stopped) OperationResult(true, messages.stopped, skipped = true) else {
                            // Persist the plan before handing the mutation to its operation journal.
                            store.started(id, item.key)
                            try { execute(item, id) }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { OperationResult(false, e.message ?: messages.failed) }
                            finally { onAttemptFinished(item) }
                        }
                        store.finished(id, item.key, result, cancelled = stopped)
                        mutableActive.update { it?.copy(completed = index + 1) }
                        result
                    }
                    store.complete(id)
                    BatchResult(results, id)
                } finally { mutableActive.value = null }
            }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (persisted) try { store.interrupt(id) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                BatchResult(listOf(OperationResult(false, messages.storageFailed)), if (persisted) id else null)
            }
        }.await()
    }
}
