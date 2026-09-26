package io.github.samolego.canta.ops

import android.content.Context
import io.github.samolego.canta.R
import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/** Serializes privacy/system changes and refuses mutations that cannot first be journaled. */
class OperationJournal(private val context: Context, private val history: HistoryStore) {
    private val mutex = history.mutationMutex
    suspend fun run(packageName: String, userId: Int, action: String, batchId: String,
        undoOf: String = "", snapshot: suspend () -> JSONObject,
        mutate: suspend (JSONObject) -> OperationResult,
    ): OperationResult = withContext(Dispatchers.IO) { mutex.withLock {
        val id = UUID.randomUUID().toString()
        var previous = JSONObject()
        var error: Exception? = null
        try { previous = SnapshotState.version(snapshot()) } catch (e: Exception) { error = e }
        try {
            history.append(OperationRecord.newBuilder().setId(id).setBatchId(batchId)
                .setTimestampMs(System.currentTimeMillis()).setUserId(userId).setPackageName(packageName)
                .setAction(action).setPreviousState(previous.toString()).setUndoOf(undoOf).build())
        } catch (e: Exception) {
            LogUtils.e("OperationJournal", "Cannot persist intent", e)
            return@withContext OperationResult(false, context.getString(R.string.operation_history_failed))
        }
        withContext(NonCancellable) {
            LogUtils.i("OperationJournal", "$action $packageName user=$userId batch=$batchId")
            val result = try { error?.let { throw it }; mutate(previous) }
            catch (e: Exception) { OperationResult(false, (e.cause ?: e).message ?: context.getString(R.string.operation_failed)) }
            val after = runCatching { SnapshotState.version(snapshot()) }.getOrNull()
            // Unknown post-state is a failure with an outstanding recovery obligation.
            val changed = previous.length() > 0 && (after == null || !SnapshotState.equal(previous, after))
            val verified = if (after == null && result.success) result.copy(success = false,
                message = context.getString(R.string.operation_not_applied)) else result
            try { history.complete(id, verified.success, verified.message, after?.toString() ?: "{}", changed) }
            catch (e: Exception) {
                LogUtils.e("OperationJournal", "Result remains pending", e)
                return@withContext OperationResult(false, context.getString(R.string.operation_history_failed), changed)
            }
            if (verified.success) LogUtils.i("OperationJournal", "$action verified")
            else LogUtils.e("OperationJournal", "$action failed: ${verified.message}")
            verified.copy(changed = changed)
        }
    } }
}
