package io.github.samolego.canta.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import io.github.samolego.canta.data.proto.CantaPreset
import io.github.samolego.canta.data.proto.PresetLockdown
import io.github.samolego.canta.data.proto.PresetsList
import io.github.samolego.canta.ops.PresetImportReview
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.LockdownSettings
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.PresetJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object PresetsListSerializer : Serializer<PresetsList> {
    override val defaultValue: PresetsList = PresetsList.getDefaultInstance()
    override suspend fun readFrom(input: InputStream): PresetsList = try {
        PresetsList.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read files.", exception)
    }
    override suspend fun writeTo(t: PresetsList, output: OutputStream) = t.writeTo(output)
}

private val Context.presetDataStore: DataStore<PresetsList> by
    dataStore(fileName = "presets.pb", serializer = PresetsListSerializer)

data class PresetLibraryState(val presets: List<CantaPresetData> = emptyList(), val loaded: Boolean = false, val error: String? = null)
enum class PresetImportResult { SAVED, REVIEW_CHANGED, FAILED }

class PresetStore(private val dataStore: DataStore<PresetsList>) {
    constructor(context: Context) : this(context.applicationContext.presetDataStore)

    companion object {
        private const val TAG = "PresetStore"
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile private var instance: PresetStore? = null
        fun getInstance(context: Context): PresetStore = instance ?: synchronized(this) {
            instance ?: PresetStore(context.applicationContext).also { instance = it }
        }
    }

    val presetsFlow: Flow<List<CantaPresetData>> = dataStore.data.map { list -> list.presetsList.map { it.toData() } }
    private var observation: StateFlow<PresetLibraryState>? = null

    /** The application owns one migration and one DataStore collector across navigation/rotation. */
    @Synchronized
    fun initialize(scope: CoroutineScope = applicationScope): StateFlow<PresetLibraryState> {
        observation?.let { return it }
        val state = MutableStateFlow(PresetLibraryState())
        val result = state.asStateFlow()
        observation = result
        scope.launch {
            try {
                migratePresetsIfNeeded()
                presetsFlow.collect { state.value = PresetLibraryState(it, loaded = true) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.value = state.value.copy(error = e.message ?: e.toString()) }
        }
        return result
    }

    /** Preserve all legacy fields while assigning IDs exactly once. */
    suspend fun migratePresetsIfNeeded() {
        dataStore.updateData { current ->
            if (current.presetsList.none { it.uuid.isEmpty() }) current
            else current.toBuilder().clearPresets().addAllPresets(current.presetsList.map {
                if (it.uuid.isEmpty()) it.toBuilder().setUuid(UUID.randomUUID().toString()).build() else it
            }).build()
        }
    }

    suspend fun savePreset(preset: CantaPresetData): Boolean = write {
        val captured = preset.ensureUuid()
        dataStore.updateData { current ->
            require(current.presetsList.none { it.uuid == captured.uuid }) { "Preset identity already exists" }
            current.toBuilder().addPresets(captured.toProto()).build()
        }
    }

    /** Compare and save in one transaction; a changed library always requires renewed review. */
    suspend fun saveReviewedImport(review: PresetImportReview): PresetImportResult {
        var outcome = PresetImportResult.REVIEW_CHANGED
        val saved = write {
            dataStore.updateData { current ->
                val matching = current.presetsList.filter { it.uuid == review.preset.uuid }
                val unchanged = if (review.previous == null) matching.isEmpty()
                    else matching.size == 1 && matching.single().toData() == review.previous
                if (!unchanged) current
                else {
                    outcome = PresetImportResult.SAVED
                    current.toBuilder().clearPresets()
                        .addAllPresets(current.presetsList.filterNot { it.uuid == review.preset.uuid })
                        .addPresets(review.preset.toProto()).build()
                }
            }
        }
        return if (saved) outcome else PresetImportResult.FAILED
    }

    suspend fun deletePreset(preset: CantaPresetData): Boolean = write {
        dataStore.updateData { current -> current.toBuilder().clearPresets()
            .addAllPresets(current.presetsList.filterNot { it.matches(preset) }).build() }
    }

    suspend fun updatePreset(oldPreset: CantaPresetData, newPreset: CantaPresetData): Boolean = write {
        dataStore.updateData { current ->
            require(current.presetsList.any { it.matches(oldPreset) }) { "Preset no longer exists" }
            val updated = newPreset.copy(uuid = oldPreset.uuid).ensureUuid()
            current.toBuilder().clearPresets().addAllPresets(current.presetsList.map {
                if (it.matches(oldPreset)) updated.toProto() else it
            }).build()
        }
    }

    suspend fun setPresetApps(preset: CantaPresetData, newApps: Set<String>): Boolean =
        updatePreset(preset, preset.copy(apps = newApps, lockdown = preset.lockdown.filterNot { it.packageName in newApps }))

    fun exportToJson(preset: CantaPresetData): String = PresetJson.encode(preset)
    fun importFromJson(jsonString: String): CantaPresetData? = try { PresetJson.decode(jsonString) }
        catch (e: Exception) { LogUtils.e(TAG, "Failed to import preset", e); null }

    fun createPresetFromUninstalledApps(apps: Set<String>, name: String, description: String) =
        CantaPresetData(name, description, System.currentTimeMillis(), apps, uuid = UUID.randomUUID().toString())

    fun formatDate(timestamp: Long): String = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))

    private suspend fun write(operation: suspend () -> Unit): Boolean = try {
        operation(); true
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { LogUtils.e(TAG, "Failed to write preset", e); false }

    private fun CantaPresetData.ensureUuid() = if (uuid.isBlank()) copy(uuid = UUID.randomUUID().toString()) else this
    private fun CantaPreset.matches(preset: CantaPresetData) = if (uuid.isNotEmpty() && preset.uuid.isNotEmpty()) uuid == preset.uuid
        else name == preset.name && createdDate == preset.createdDate

    private fun CantaPresetData.toProto(): CantaPreset = CantaPreset.newBuilder()
        .setName(name).setDescription(description).setCreatedDate(createdDate).addAllApps(apps)
        .setVersion(version).setUuid(uuid).addAllLockdown(lockdown.map { entry ->
            PresetLockdown.newBuilder().setPackageName(entry.packageName)
                .setRevokePermissions(entry.revokePermissions).setRestrictBackground(entry.restrictBackground)
                .setDenyMetered(entry.denyMetered).setBlockNetwork(entry.blockNetwork).build()
        }).apply { this@toProto.profileKind?.let { setProfileKind(it) } }.build()

    private fun CantaPreset.toData() = CantaPresetData(
        name = name, description = description, createdDate = createdDate, apps = appsList.toSet(),
        version = version.ifEmpty { "1.0" }, uuid = uuid,
        lockdown = lockdownList.map { LockdownSettings(it.packageName, it.revokePermissions, it.restrictBackground, it.denyMetered, it.blockNetwork) },
        profileKind = profileKind.takeIf { hasProfileKind() && it.isNotBlank() },
    )
}
