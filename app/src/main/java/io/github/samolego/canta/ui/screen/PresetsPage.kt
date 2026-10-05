package io.github.samolego.canta.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.StatusChip
import io.github.samolego.canta.ui.dialog.preset.ImportPresetDialog
import io.github.samolego.canta.ui.dialog.preset.PresetCreateDialog
import io.github.samolego.canta.ui.dialog.preset.PresetImportReviewDialog
import io.github.samolego.canta.ui.dialog.preset.PresetEditDialog
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PresetsViewModel
import io.github.samolego.canta.util.CantaPresetData

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PresetsPage(
    presetViewModel: PresetsViewModel,
    onNavigateBack: (appliedPreset: CantaPresetData?) -> Unit,
    appListViewModel: AppListViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiMessages = remember { CanaServices.getInstance().uiMessages }

    var currentDialog by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var presetToDelete by remember { mutableStateOf<CantaPresetData?>(null) }
    val presetDeletedText = stringResource(R.string.preset_deleted)
    val presetDeleteErrorText = stringResource(R.string.preset_delete_error)

    val createConfigDialog =
            @Composable
            {
                PresetCreateDialog(
                        appListViewModel = appListViewModel,
                        presetViewModel = presetViewModel,
                        closeDialog = { currentDialog = null }
                )
            }

    LaunchedEffect(Unit) { presetViewModel.initialize(context) }
    Scaffold(
            snackbarHost = { androidx.compose.material3.SnackbarHost(io.github.samolego.canta.ui.LocalCanaSnackbar.current) },
            topBar = {
                ScreenTopBar(
                        onNavigateBack = { onNavigateBack(null) },
                        title = { Text(stringResource(R.string.presets)) },
                )
            }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (presetViewModel.libraryError != null) {
                Text(stringResource(R.string.preset_library_error), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
            } else if (presetViewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (presetViewModel.presets.isEmpty()) {
                EmptyPresetsState(
                        onCreateClick = { currentDialog = { createConfigDialog() } },
                        onImportClick = {
                            currentDialog = {
                                ImportDialog(
                                        presetViewModel = presetViewModel,
                                        appListViewModel = appListViewModel,
                                        hideDialog = { currentDialog = null },
                                        context = context,
                                )
                            }
                        }
                )
            } else {
                val presetCopiedText = stringResource(R.string.preset_copied)
                LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { currentDialog = { createConfigDialog() } }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.create_preset))
                            }
                            OutlinedButton(onClick = {
                                currentDialog = {
                                    ImportDialog(presetViewModel = presetViewModel, appListViewModel = appListViewModel,
                                        hideDialog = { currentDialog = null }, context = context)
                                }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.import_preset))
                            }
                        }
                    }
                    items(presetViewModel.presets) { preset ->
                        PresetCard(
                                preset = preset,
                                onAddApps = {
                                    presetViewModel.editingPreset = preset
                                    onNavigateBack(preset)
                                },
                                formatDate = presetViewModel::formatDate,
                                onEdit = {
                                    currentDialog = {
                                        PresetEditDialog(
                                                preset = preset,
                                                presetViewModel = presetViewModel,
                                                closeDialog = { currentDialog = null }
                                        )
                                    }
                                },
                                onApply = {
                                    val user = appListViewModel.selectedUserId
                                    currentDialog = {
                                        io.github.samolego.canta.ui.dialog.preset.PresetApplyDialog(preset, user) {
                                            currentDialog = null
                                            scope.launch { appListViewModel.loadInstalled(context.packageManager, context) }
                                        }
                                    }
                                },
                                onDelete = { presetToDelete = preset },
                                onExport = {
                                    presetViewModel.exportToClipboard(
                                            context = context,
                                            preset = preset,
                                    )
                                    uiMessages.post(presetCopiedText)
                                },
                        )
                    }
                }
            }
        }
    }

    currentDialog?.let { it() }
    presetToDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text(stringResource(R.string.preset_delete_title)) },
            text = { Text(stringResource(R.string.preset_delete_description, preset.name)) },
            confirmButton = {
                TextButton(onClick = {
                    presetToDelete = null
                    presetViewModel.deletePreset(preset,
                        onSuccess = { uiMessages.post(presetDeletedText) },
                        onError = { uiMessages.post(presetDeleteErrorText) })
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { presetToDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    presetViewModel.importReview?.let { review ->
        val importedText = stringResource(R.string.preset_import_saved)
        PresetImportReviewDialog(review, presetViewModel.checkingImport, presetViewModel.isLoading,
            presetViewModel.importError,
            onConfirm = { presetViewModel.confirmImport { uiMessages.post(importedText) } },
            onDismiss = presetViewModel::cancelImport)
    }
}

@Composable
private fun ImportDialog(
    hideDialog: () -> Unit,
    presetViewModel: PresetsViewModel,
    appListViewModel: AppListViewModel,
    context: Context,
) {
    val importFailedText = stringResource(R.string.import_failed)
    val uiMessages = remember { CanaServices.getInstance().uiMessages }
    var importError by remember { mutableStateOf<String?>(null) }
    val review: (CantaPresetData) -> Unit = { preset ->
        val profile = appListViewModel.selectedProfile
            ?: appListViewModel.users.firstOrNull { it.id == appListViewModel.selectedUserId }
        presetViewModel.prepareImport(preset, context, appListViewModel.selectedUserId, profile?.name, profile?.kind?.name)
        hideDialog()
    }
    val failure = { importError = importFailedText; uiMessages.post(importFailedText) }
    ImportPresetDialog(
        onDismiss = hideDialog,
        onImportFromClipboard = { presetViewModel.importFromClipboard(context, review, failure) },
        onImportFromText = { presetViewModel.importFromJson(it, review, failure) },
        importError = importError,
        onTextChanged = { importError = null },
    )
}

@Composable
private fun EmptyPresetsState(onCreateClick: () -> Unit, onImportClick: () -> Unit) {
    Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Surface(
                modifier = Modifier.size(80.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
                text = stringResource(R.string.no_presets),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
        )

        Text(
                text = stringResource(R.string.presets_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onCreateClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.create_preset))
            }

            OutlinedButton(onClick = onImportClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.import_preset))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetCard(
    preset: CantaPresetData,
    onEdit: (CantaPresetData) -> Unit,
    onAddApps: (CantaPresetData) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onApply: () -> Unit,
    formatDate: (Long) -> String
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = preset.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                    )

                    if (preset.description.isNotEmpty()) {
                        Text(
                                text = preset.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    preset.profileKind?.let { kind ->
                        StatusChip(kind.lowercase().replaceFirstChar { it.titlecase() }, Icons.Default.Person,
                            modifier = Modifier.padding(top = 8.dp))
                    }

                    FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                    Icons.Default.Apps,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                    text = pluralStringResource(R.plurals.num_selected_apps, preset.apps.size + preset.lockdown.size, preset.apps.size + preset.lockdown.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                    text = formatDate(preset.createdDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Action menu button
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit)) },
                                onClick = {
                                    showMenu = false
                                    onEdit(preset)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                        )
                        DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_apps)) },
                                onClick = {
                                    showMenu = false
                                    onAddApps(preset)
                                },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                        )
                        DropdownMenuItem(
                                text = { Text(stringResource(R.string.preset_copy_action)) },
                                onClick = {
                                    showMenu = false
                                    onExport()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                colors =
                                        MenuDefaults.itemColors(
                                                textColor = MaterialTheme.colorScheme.error
                                        )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary action button
            Button(
                    onClick = { onApply() },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                            )
            ) {
                Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.apply_preset))
            }
        }
    }
}
