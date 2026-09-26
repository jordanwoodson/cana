package io.github.samolego.canta.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.samolego.canta.data.PresetStore
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.data.PresetImportResult
import io.github.samolego.canta.extension.getAllPackagesInfo
import io.github.samolego.canta.ops.PresetImportReview
import io.github.samolego.canta.ops.PresetPreview
import io.github.samolego.canta.ops.PresetProfileInventory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class PresetsViewModel : ViewModel() {

    companion object {
        private const val TAG = "PresetsViewModel"
    }

    var editingPreset by mutableStateOf<CantaPresetData?>(null)

    private lateinit var presetStore: PresetStore

    private val _presets = mutableStateOf<List<CantaPresetData>>(emptyList())
    val presets: List<CantaPresetData>
        get() = _presets.value

    private var saving by mutableStateOf(false)
    private var libraryLoading by mutableStateOf(true)
    val isLoading: Boolean get() = saving || libraryLoading

    var libraryError by mutableStateOf<String?>(null)
        private set
    var importReview by mutableStateOf<PresetImportReview?>(null)
        private set
    var checkingImport by mutableStateOf(false)
        private set
    var importError by mutableStateOf<PresetImportResult?>(null)
        private set

    fun initialize(context: Context) {
        if (::presetStore.isInitialized) return
        presetStore = PresetStore.getInstance(context.applicationContext)
        libraryLoading = true
        viewModelScope.launch {
            presetStore.initialize().collect { state ->
                _presets.value = state.presets
                libraryError = state.error
                libraryLoading = !state.loaded && state.error == null
            }
        }
    }

    fun prepareImport(preset: CantaPresetData, context: Context, userId: Int, profileName: String?, profileKind: String?) {
        if (isLoading || libraryError != null) return
        val inventory = PresetProfileInventory(userId, profileName, profileKind)
        val review = PresetPreview.reviewImport(preset, presets, listOf(inventory))
        importReview = review
        importError = null
        checkingImport = true
        val applicationContext = context.applicationContext
        viewModelScope.launch {
            val checked = try {
                val state = io.github.samolego.canta.ops.CanaServices.getInstance().inventory.snapshot(userId)
                if (!state.loaded || state.stale || state.error != null) inventory else inventory.copy(
                    installedPackages = state.apps.filterNot { it.isUninstalled }.map { it.packageName }.toSet(),
                    knownPackages = state.apps.map { it.packageName }.toSet())
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { inventory }
            if (importReview === review) {
                importReview = review.copy(profiles = PresetPreview.forProfiles(review.preset, listOf(checked)))
                checkingImport = false
            }
        }
    }

    fun cancelImport() {
        if (isLoading) return
        importReview = null
        importError = null
        checkingImport = false
    }

    fun confirmImport(onSuccess: () -> Unit) {
        val reviewed = importReview ?: return
        if (isLoading || checkingImport || libraryError != null) return
        saving = true
        viewModelScope.launch {
            try {
                when (val outcome = presetStore.saveReviewedImport(reviewed)) {
                    PresetImportResult.SAVED -> { importReview = null; importError = null; onSuccess() }
                    PresetImportResult.REVIEW_CHANGED -> {
                        val current = presetStore.presetsFlow.first()
                        importReview = PresetPreview.reviewImport(reviewed.preset, current, emptyList()).copy(profiles = reviewed.profiles)
                        importError = outcome
                    }
                    PresetImportResult.FAILED -> importError = outcome
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { importError = PresetImportResult.FAILED }
            finally { saving = false }
        }
    }

    fun savePreset(
        name: String,
        description: String,
        apps: Set<String>,
        privacyUserId: Int? = null,
        profileKind: String? = null,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        if (isLoading) return
        saving = true
        viewModelScope.launch {
            try {
                val lockdown = privacyUserId?.let { io.github.samolego.canta.ops.CanaServices.getInstance().presets.captureLockdown(it) }.orEmpty()
                val preset = presetStore.createPresetFromUninstalledApps(apps, name, description)
                    .copy(lockdown = lockdown.filterNot { it.packageName in apps }, profileKind = profileKind)
                if (presetStore.savePreset(preset)) onSuccess() else onError()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { LogUtils.e(TAG, "Cannot capture or save preset", e); onError() }
            finally { saving = false }
        }
    }

    fun deletePreset(preset: CantaPresetData, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val success = presetStore.deletePreset(preset)
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }

    fun exportToClipboard(
        context: Context,
        preset: CantaPresetData,
    ) {
        val jsonString = presetStore.exportToJson(preset)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Cana Preset", jsonString)
        clipboard.setPrimaryClip(clip)
    }

    fun importFromClipboard(
        context: Context,
        onSuccess: (CantaPresetData) -> Unit,
        onError: () -> Unit
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip

        if (clipData != null && clipData.itemCount > 0) {
            val jsonString = clipData.getItemAt(0).text.toString()
            val preset = presetStore.importFromJson(jsonString)

            if (preset != null) {
                onSuccess(preset)
            } else {
                onError()
            }
        } else {
            onError()
        }
    }

    fun importFromJson(
        jsonString: String,
        onSuccess: (CantaPresetData) -> Unit,
        onError: () -> Unit
    ) {
        val preset = presetStore.importFromJson(jsonString)
        if (preset != null) {
            onSuccess(preset)
        } else {
            onError()
        }
    }

    fun formatDate(timestamp: Long): String {
        return presetStore.formatDate(timestamp)
    }

    fun updatePreset(
        oldPreset: CantaPresetData,
        newName: String,
        newDescription: String,
        profileKind: String? = oldPreset.profileKind,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        viewModelScope.launch {
            val updatedPreset =
                oldPreset.copy(
                    name = newName,
                    description = newDescription,
                    profileKind = profileKind,
                    apps = oldPreset.apps
                )

            val success = presetStore.updatePreset(oldPreset, updatedPreset)
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }

    fun setPresetApps(
        preset: CantaPresetData,
        newApps: Set<String>,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        viewModelScope.launch {
            val success = presetStore.setPresetApps(preset, newApps)
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }

}
