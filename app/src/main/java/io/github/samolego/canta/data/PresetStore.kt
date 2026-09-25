package io.github.samolego.canta.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import io.github.samolego.canta.data.proto.CantaPreset
import io.github.samolego.canta.data.proto.PresetsList
import io.github.samolego.canta.extension.getInfoForPackage
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.LockdownSettings
import io.github.samolego.canta.util.PresetJson
import io.github.samolego.canta.data.proto.PresetLockdown
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object PresetsListSerializer : Serializer<PresetsList> {
    override val defaultValue: PresetsList = PresetsList.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): PresetsList {
        try {
            return PresetsList.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read files.", exception)
        }
    }

    override suspend fun writeTo(t: PresetsList, output: OutputStream) = t.writeTo(output)
}

private val Context.presetDataStore: DataStore<PresetsList> by
dataStore(fileName = "presets.pb", serializer = PresetsListSerializer)

class PresetStore(private val context: Context) {

    companion object {
        private const val TAG = "PresetStore"
    }

    val presetsFlow: Flow<List<CantaPresetData>> =
        context.presetDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    LogUtils.e(TAG, "Error reading presets.", exception)
                    emit(PresetsList.getDefaultInstance())
                } else {
                    throw exception
                }
            }
            .map { presetsList ->
                presetsList.presetsList.map { protoPreset ->
                    CantaPresetData(
                        name = protoPreset.name,
                        description = protoPreset.description,
                        createdDate = protoPreset.createdDate,
                        apps = protoPreset.appsList.toSet(),
                        version = protoPreset.version.ifEmpty { "1.0" },
                        lockdown = protoPreset.lockdownList.map { LockdownSettings(it.packageName, it.revokePermissions, it.restrictBackground, it.denyMetered, it.blockNetwork) },
                        uuid = protoPreset.uuid
                    )
                }
            }

    /**
     * One-time migration for presets created before unique IDs were introduced.
     * Assigns a UUID to every preset that does not already have one.
     */
    suspend fun migratePresetsIfNeeded() {
        try {
            context.presetDataStore.updateData { currentPresets ->
                var needsUpdate = false
                val migratedPresets =
                    currentPresets.presetsList.map { protoPreset ->
                        if (protoPreset.uuid.isEmpty()) {
                            needsUpdate = true
                            protoPreset.toBuilder().setUuid(generateUuid()).build()
                        } else {
                            protoPreset
                        }
                    }

                if (needsUpdate) {
                    LogUtils.i(TAG, "Migrated ${migratedPresets.size} presets to use UUIDs")
                    currentPresets
                        .toBuilder()
                        .clearPresets()
                        .addAllPresets(migratedPresets)
                        .build()
                } else {
                    currentPresets
                }
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to migrate presets: ${e.message}")
        }
    }

    suspend fun savePreset(preset: CantaPresetData): Boolean {
        return try {
            context.presetDataStore.updateData { currentPresets ->
                val presetWithUuid = preset.ensureUuid()
                val protoPreset =
                    CantaPreset.newBuilder()
                        .setName(presetWithUuid.name)
                        .setDescription(presetWithUuid.description)
                        .setCreatedDate(presetWithUuid.createdDate)
                        .addAllApps(presetWithUuid.apps)
                        .setVersion(presetWithUuid.version)
                        .setUuid(presetWithUuid.uuid)
                        .addAllLockdown(presetWithUuid.lockdown.map { it.toProto() })
                        .build()

                currentPresets.toBuilder().addPresets(protoPreset).build()
            }
            LogUtils.i(TAG, "Preset saved: ${preset.name}")
            true
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to save preset: ${e.message}")
            false
        }
    }

    suspend fun deletePreset(preset: CantaPresetData): Boolean {
        return try {
            context.presetDataStore.updateData { currentPresets ->
                currentPresets
                    .toBuilder()
                    .clearPresets()
                    .addAllPresets(
                        currentPresets.presetsList.filter { protoPreset ->
                            !protoPreset.matches(preset)
                        }
                    )
                    .build()
            }
            LogUtils.i(TAG, "Preset deleted: ${preset.name}")
            true
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to delete preset: ${e.message}")
            false
        }
    }

    suspend fun updatePreset(
        oldPreset: CantaPresetData,
        newPreset: CantaPresetData
    ): Boolean {
        return try {
            context.presetDataStore.updateData { currentPresets ->
                val updatedPresets =
                    currentPresets.presetsList.map { protoPreset ->
                        if (protoPreset.matches(oldPreset)) {
                            val presetWithUuid = newPreset.ensureUuid()
                            CantaPreset.newBuilder()
                                .setName(presetWithUuid.name)
                                .setDescription(presetWithUuid.description)
                                .setCreatedDate(presetWithUuid.createdDate)
                                .addAllApps(presetWithUuid.apps)
                                .setVersion(presetWithUuid.version)
                                .setUuid(presetWithUuid.uuid)
                        .addAllLockdown(presetWithUuid.lockdown.map { it.toProto() })
                                .build()
                        } else {
                            protoPreset
                        }
                    }

                currentPresets.toBuilder().clearPresets().addAllPresets(updatedPresets).build()
            }
            LogUtils.i(TAG, "Preset updated: ${newPreset.name}")
            true
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to update preset: ${e.message}")
            false
        }
    }

    suspend fun setPresetApps(
        preset: CantaPresetData,
        newApps: Set<String>
    ): Boolean {
        val updatedPreset = preset.copy(apps = newApps, lockdown = preset.lockdown.filterNot { it.packageName in newApps })
        return updatePreset(preset, updatedPreset)
    }

    fun exportToJson(preset: CantaPresetData): String = PresetJson.encode(preset)

    fun importFromJson(jsonString: String): CantaPresetData? = try { PresetJson.decode(jsonString) }
    catch (e: Exception) { LogUtils.e(TAG, "Failed to import preset", e); null }

    private fun LockdownSettings.toProto(): PresetLockdown = PresetLockdown.newBuilder()
        .setPackageName(packageName).setRevokePermissions(revokePermissions).setRestrictBackground(restrictBackground)
        .setDenyMetered(denyMetered).setBlockNetwork(blockNetwork).build()

    fun createPresetFromUninstalledApps(
        apps: Set<String>,
        name: String,
        description: String
    ): CantaPresetData {
        return CantaPresetData(
            name = name,
            description = description,
            createdDate = System.currentTimeMillis(),
            apps = apps,
            uuid = generateUuid()
        )
    }

    fun formatDate(timestamp: Long): String {
        val formatter =
            java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return formatter.format(java.util.Date(timestamp))
    }

    private fun generateUuid(): String = UUID.randomUUID().toString()

    private fun CantaPresetData.ensureUuid(): CantaPresetData {
        return if (uuid.isEmpty()) copy(uuid = generateUuid()) else this
    }

    private fun CantaPreset.matches(preset: CantaPresetData): Boolean {
        return if (uuid.isNotEmpty() && preset.uuid.isNotEmpty()) {
            uuid == preset.uuid
        } else {
            // Fallback for presets that have not been migrated yet
            name == preset.name && createdDate == preset.createdDate
        }
    }
}
