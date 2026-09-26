package io.github.samolego.canta.ui.dialog.preset

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PresetsViewModel
import io.github.samolego.canta.util.CantaPresetData

@Composable
private fun PresetDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    capturePrivacy: Boolean = false,
    profileKind: String? = null,
    busy: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, includePrivacy: Boolean, profileKind: String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var nameError by remember { mutableStateOf(false) }
    var includePrivacy by remember { mutableStateOf(capturePrivacy) }
    var includeProfile by remember { mutableStateOf(profileKind != null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.create_preset_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text(stringResource(R.string.preset_name)) },
                    placeholder = { Text(stringResource(R.string.preset_name_placeholder)) },
                    isError = nameError,
                    supportingText =
                    if (nameError) {
                        { Text(stringResource(R.string.preset_name_missing_error)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.optional_description)) },
                    placeholder = { Text(stringResource(R.string.preset_description_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (profileKind != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeProfile, { includeProfile = it })
                    Text(stringResource(R.string.preset_remember_profile, profileKind))
                }
                if (capturePrivacy) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includePrivacy, { includePrivacy = it })
                    Text(stringResource(R.string.preset_capture_privacy))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), description.trim(), includePrivacy, profileKind.takeIf { includeProfile })
                    } else {
                        nameError = true
                    }
                },
                enabled = name.isNotBlank() && !busy
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun PresetCreateDialog(
    appListViewModel: AppListViewModel,
    presetViewModel: PresetsViewModel,
    closeDialog: () -> Unit,
) {
    val context = LocalContext.current
    val presetSaveErrorText = stringResource(R.string.preset_save_error)
    PresetDialog(
        title = stringResource(R.string.create_preset),
        initialName = "",
        initialDescription = "",
        capturePrivacy = true,
        profileKind = appListViewModel.selectedProfile?.kind?.name
            ?: appListViewModel.users.find { it.id == appListViewModel.selectedUserId }?.kind?.name
            ?: "PERSONAL".takeIf { appListViewModel.selectedUserId == 0 },
        busy = presetViewModel.isLoading,
        onDismiss = closeDialog,
        onConfirm = { name, description, includePrivacy, kind ->
            presetViewModel.savePreset(
                name = name,
                description = description,
                privacyUserId = if (includePrivacy) appListViewModel.selectedUserId else null,
                profileKind = kind,
                apps = appListViewModel.allApps.filter { it.isUninstalled }.map { it.packageName }
                    .toSet(),
                onSuccess = { closeDialog() },
                onError = {
                    Toast.makeText(
                        context,
                        presetSaveErrorText,
                        Toast.LENGTH_SHORT
                    )
                        .show()
                },
            )
        }
    )
}

@Composable
fun PresetEditDialog(
    preset: CantaPresetData,
    presetViewModel: PresetsViewModel,
    closeDialog: () -> Unit,
) {
    val context = LocalContext.current
    val presetSaveErrorText = stringResource(R.string.preset_save_error)
    PresetDialog(
        title = stringResource(R.string.edit),
        initialName = preset.name,
        initialDescription = preset.description,
        profileKind = preset.profileKind,
        onDismiss = closeDialog,
        onConfirm = { name, description, _, kind ->
            presetViewModel.updatePreset(
                oldPreset = preset,
                newName = name,
                newDescription = description,
                profileKind = kind,
                onSuccess = { closeDialog() },
                onError = {
                    Toast.makeText(
                        context,
                        presetSaveErrorText,
                        Toast.LENGTH_SHORT
                    )
                        .show()
                },
            )
        }
    )
}
