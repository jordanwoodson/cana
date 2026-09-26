package io.github.samolego.canta.data

import android.content.Context
import androidx.datastore.core.*
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import io.github.samolego.canta.data.proto.*
import io.github.samolego.canta.ops.BatchItem
import io.github.samolego.canta.ops.OperationResult
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.InputStream
import java.io.OutputStream

val Context.batchDataStore by dataStore("operation_batches.pb", BatchSerializer)
object BatchSerializer : Serializer<PlannedBatches> {
    override val defaultValue: PlannedBatches = PlannedBatches.getDefaultInstance()
    override suspend fun readFrom(input: InputStream): PlannedBatches = try { PlannedBatches.parseFrom(input) }
        catch (e: InvalidProtocolBufferException) { throw CorruptionException("Cannot read operation batches", e) }
    override suspend fun writeTo(t: PlannedBatches, output: OutputStream) = t.writeTo(output)
}

data class BatchReadState(val batches: List<PlannedBatch> = emptyList(), val unavailable: Boolean = false)

class BatchStore(private val store: DataStore<PlannedBatches>) {
    val batches = store.data.map { it.batchesList.toList() }
    val state = batches.map { BatchReadState(it) }.catch { emit(BatchReadState(unavailable = true)) }
    suspend fun append(id: String, title: String, items: List<BatchItem>) {
        require(items.map { it.key }.distinct().size == items.size && items.isNotEmpty())
        store.updateData { state ->
            require(state.batchesList.none { it.id == id })
            val keep = state.batchesList.filter { !it.completed || it.interrupted && !it.reviewAcknowledged || it.itemsList.any { item -> item.status == "pending" } }.map { it.id }.toSet() +
                state.batchesList.filter { it.completed && (!it.interrupted || it.reviewAcknowledged) }.takeLast(199).map { it.id }
            state.toBuilder().clearBatches().addAllBatches(state.batchesList.filter { it.id in keep }).addBatches(
                PlannedBatch.newBuilder().setId(id).setTitle(title).setCreatedMs(System.currentTimeMillis())
                    .addAllItems(items.map { item -> PlannedBatchItem.newBuilder().setKey(item.key)
                        .setPackageName(item.packageName).setUserId(item.userId).setAction(item.action).setStatus("queued").build() })
            ).build()
        }
    }
    private suspend fun update(id: String, block: (PlannedBatch.Builder) -> Unit) {
        store.updateData { state ->
            val index = state.batchesList.indexOfFirst { it.id == id }; require(index >= 0)
            val batch = state.getBatches(index).toBuilder(); block(batch)
            state.toBuilder().setBatches(index, batch).build()
        }
    }
    private suspend fun item(id: String, key: String, block: (PlannedBatchItem.Builder) -> Unit) = update(id) { batch ->
        val index = batch.itemsList.indexOfFirst { it.key == key }; require(index >= 0)
        val item = batch.getItems(index).toBuilder(); block(item); batch.setItems(index, item)
    }
    suspend fun started(id: String, key: String) = item(id, key) { it.setStatus("running") }
    suspend fun finished(id: String, key: String, result: OperationResult, cancelled: Boolean = false) = item(id, key) {
        it.setStatus(if (cancelled) "cancelled" else if (result.outcomeUnknown) "pending" else "completed").setMessage(result.message)
            .setSuccess(result.success).setSkipped(result.skipped).setFreedBytes(result.freedBytes)
    }
    suspend fun requestStop(id: String) = update(id) { it.setStopRequested(true) }
    suspend fun reviewed(id: String) = update(id) { it.setReviewAcknowledged(true) }
    suspend fun complete(id: String) = update(id) { it.setCompleted(true) }
    suspend fun reconcilePending(records: List<OperationRecord>) {
        store.updateData { state -> state.toBuilder().clearBatches().addAllBatches(state.batchesList.map { batch ->
            if (batch.itemsList.none { it.status == "pending" }) batch else batch.toBuilder().clearItems().addAllItems(batch.itemsList.map { item ->
                val record = records.lastOrNull { it.batchId == batch.id && it.packageName == item.packageName &&
                    it.userId == item.userId && it.action == item.action.removePrefix("undo_") }
                if (item.status != "pending" || record?.completed != true) item else item.toBuilder()
                    .setStatus("completed").setSuccess(record.success).setMessage(record.resultMessage).setFreedBytes(record.freedBytes).build()
            }).build()
        }).build() }
    }
    suspend fun interrupt(id: String) = update(id) { batch ->
        val items = batch.itemsList.toList()
        batch.setInterrupted(true).setCompleted(true).clearItems().addAllItems(items.map { item ->
            if (item.status in setOf("queued", "running")) item.toBuilder()
                .setStatus(if (item.status == "running") "interrupted" else "not_started").build() else item
        })
    }
    suspend fun markInterrupted() {
        store.updateData { state -> state.toBuilder().clearBatches().addAllBatches(state.batchesList.map { batch ->
            if (batch.completed) batch else batch.toBuilder().setInterrupted(true).setCompleted(true)
                .clearItems().addAllItems(batch.itemsList.map { item ->
                    if (item.status in setOf("queued", "running")) item.toBuilder().setStatus(if (item.status == "running") "interrupted" else "not_started").build() else item
                }).build()
        }).build() }
    }
}
