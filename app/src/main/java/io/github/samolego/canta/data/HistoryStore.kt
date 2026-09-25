package io.github.samolego.canta.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import io.github.samolego.canta.data.proto.OperationHistory
import io.github.samolego.canta.data.proto.OperationRecord
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream

val Context.historyDataStore: DataStore<OperationHistory> by dataStore(
    fileName = "operation_history.pb", serializer = HistorySerializer,
)

object HistorySerializer : Serializer<OperationHistory> {
    override val defaultValue: OperationHistory = OperationHistory.getDefaultInstance()
    override suspend fun readFrom(input: InputStream): OperationHistory = try {
        OperationHistory.parseFrom(input)
    } catch (e: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read operation history", e)
    }
    override suspend fun writeTo(t: OperationHistory, output: OutputStream) = t.writeTo(output)
}

class HistoryStore(private val dataStore: DataStore<OperationHistory>) {
    val records = dataStore.data.map { it.recordsList.toList() }

    suspend fun append(record: OperationRecord) {
        require(record.id.isNotBlank())
        dataStore.updateData {
            require(it.recordsList.none { existing -> existing.id == record.id }) { "Duplicate history id" }
            it.toBuilder().addRecords(record).build()
        }
    }

    suspend fun complete(id: String, success: Boolean, message: String, afterState: String, changed: Boolean) {
        dataStore.updateData { history ->
            val index = history.recordsList.indexOfFirst { it.id == id }
            require(index >= 0) { "Unknown history id: $id" }
            val updated = history.getRecords(index).toBuilder().setCompleted(true)
                .setSuccess(success).setResultMessage(message).setAfterState(afterState)
                .setChanged(changed).build()
            history.toBuilder().setRecords(index, updated).build()
        }
    }
}
