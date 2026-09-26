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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
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

data class HistoryReadState(val records: List<OperationRecord> = emptyList(),
    val quarantinedRecords: List<OperationRecord> = emptyList(), val unavailable: Boolean = false)

class HistoryStore(private val dataStore: DataStore<OperationHistory>, private val archive: HistoryArchive? = null,
    private val completedLimit: Int = 500,
    private val installationId: String? = null,
) {
    init { require(completedLimit > 0) }
    /** One platform transaction at a time across package, privacy and system journals. */
    val mutationMutex = Mutex()
    val records = dataStore.data.map {
        if (installationId != null && it.installationId != installationId) emptyList() else it.recordsList.toList()
    }
    val quarantinedRecords = dataStore.data.map { it.quarantinedRecordsList.toList() }
    /** Presentation may show unavailable; privileged callers keep using the throwing records flow. */
    val state = dataStore.data.map {
        HistoryReadState(if (installationId != null && it.installationId != installationId) emptyList() else it.recordsList.toList(),
            it.quarantinedRecordsList.toList())
    }.catch { emit(HistoryReadState(unavailable = true)) }

    suspend fun bindInstallation(id: String) {
        require(id.isNotBlank())
        dataStore.updateData { current ->
            if (current.installationId == id) current else current.toBuilder().setInstallationId(id)
                .addAllQuarantinedRecords(current.recordsList).clearRecords().build()
        }
    }

    private suspend fun ensureInstallation() { installationId?.let { bindInstallation(it) } }

    /** Includes cold records for complete recovery export; quarantined records require explicit review. */
    suspend fun allRecords(): List<OperationRecord> = withContext(Dispatchers.IO) {
        ensureInstallation()
        (archive?.read().orEmpty() + records.first()).distinctBy { it.id }.sortedBy { it.sequenceNumber }
    }

    suspend fun append(record: OperationRecord) {
        ensureInstallation()
        require(record.id.isNotBlank())
        dataStore.updateData {
            require(it.recordsList.none { existing -> existing.id == record.id }) { "Duplicate history id" }
            val sequence = maxOf(it.nextSequence, it.recordsList.maxOfOrNull { existing -> existing.sequenceNumber } ?: 0) + 1
            it.toBuilder().setNextSequence(sequence).addRecords(record.toBuilder().setSequenceNumber(sequence).build()).build()
        }
    }

    suspend fun complete(id: String, success: Boolean, message: String, afterState: String, changed: Boolean, freedBytes: Long = 0, recoveryComplete: Boolean = false) {
        ensureInstallation()
        dataStore.updateData { history ->
            val index = history.recordsList.indexOfFirst { it.id == id }
            require(index >= 0) { "Unknown history id: $id" }
            val updated = history.getRecords(index).toBuilder().setCompleted(true)
                .setOutcomeUnknown(false).setSuccess(success).setResultMessage(message.take(4096)).setAfterState(afterState)
                .setChanged(changed).setFreedBytes(freedBytes).setRecoveryComplete(recoveryComplete).build()
            retain(history.toBuilder().setRecords(index, updated).build())
        }
    }

    suspend fun markPending(id: String, message: String, afterState: String, changed: Boolean = true, installerRequestId: String = "") {
        ensureInstallation()
        dataStore.updateData { history ->
            val index = history.recordsList.indexOfFirst { it.id == id }
            require(index >= 0) { "Unknown history id: $id" }
            history.toBuilder().setRecords(index, history.getRecords(index).toBuilder()
                .setCompleted(false).setSuccess(false).setOutcomeUnknown(true).setChanged(changed)
                .setInstallerRequestId(installerRequestId)
                .setResultMessage(message.take(4096)).setAfterState(afterState).build()).build()
        }
    }

    private fun retain(history: OperationHistory): OperationHistory {
        val records = history.recordsList
        val recovered = records.filter { it.undoOf.isNotEmpty() && it.completed && (it.success || it.recoveryComplete) }.map { it.undoOf }.toSet()
        val unresolved = records.filter { !it.completed || it.outcomeUnknown || (!it.success && it.changed && !it.recoveryComplete && it.id !in recovered) }
        val retained = (unresolved + records.filterNot { it in unresolved }.takeLast(completedLimit)).map { it.id }.toMutableSet()
        // A live record must retain its successful undo marker, otherwise it becomes undoable again.
        retained += records.filter { it.undoOf in retained }.map { it.id }
        val cold = records.filter { it.id !in retained }
        // Without an archive, never discard an operation that is needed to reconstruct state.
        val removed = if (archive == null) cold.filter { !it.changed && it.undoOf.isBlank() } else cold
        archive?.append(removed)
        val removedIds = removed.map { it.id }.toSet()
        return history.toBuilder().clearRecords().addAllRecords(records.filter { it.id !in removedIds })
            .setArchivedCount(history.archivedCount + if (archive != null) removed.size else 0).build()
    }
}
