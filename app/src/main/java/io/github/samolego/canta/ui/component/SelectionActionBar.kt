package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.SelectionSummary
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.ui.menu.displayName

@Composable
fun profileLabel(model: AppListViewModel): String = model.selectedProfile?.let {
    stringResource(R.string.profile_context, it.displayName(), it.id)
} ?: stringResource(R.string.profile_current, model.selectedUserId)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionActionBar(model: AppListViewModel, appType: AppsType) {
    if (model.selectedApps.isEmpty()) return
    var actions by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf(false) }
    val visible = model.appList.filter { it.isUninstalled == (appType == AppsType.UNINSTALLED) }.map { it.packageName }.toSet()
    val summary = SelectionSummary.of(model.selectedApps.keys.toSet(), visible)
    Surface(tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(profileLabel(model), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.selection_summary, summary.total, summary.hidden), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !model.isOperating, onClick = { model.selectedApps.clear() }) { Text(stringResource(R.string.clear_selection)) }
                TextButton(onClick = { review = true }) { Text(stringResource(R.string.review_selection)) }
                Box {
                    Button(enabled = !model.isOperating, onClick = { actions = true }) { Text(stringResource(R.string.review_actions)) }
                    DropdownMenu(actions, { actions = false }) {
                        val choices = if (appType == AppsType.UNINSTALLED) listOf(PackageAction.REINSTALL, PackageAction.REMOVE_UPDATES)
                        else listOf(PackageAction.DISABLE, PackageAction.ENABLE, PackageAction.SUSPEND, PackageAction.UNSUSPEND, PackageAction.UNINSTALL_KEEP_DATA, PackageAction.UNINSTALL)
                        choices.forEach { action -> DropdownMenuItem(text = { Text(stringResource(action.label)) }, onClick = { actions = false; model.requestAction(action) }) }
                    }
                }
            }
        }
    }
    if (review) AlertDialog(onDismissRequest = { review = false }, title = { Text(stringResource(R.string.review_selection)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(model.selectedApps.keys.toList(), key = { it }) { name ->
                    val app = model.allApps.find { it.packageName == name }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(app?.name ?: name)
                            Text(app?.packageName ?: stringResource(R.string.selected_unavailable), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(enabled = !model.isOperating, onClick = { model.selectedApps.remove(name) }) {
                            Icon(Icons.Default.Close, stringResource(R.string.clear_selection))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { review = false }) { Text(stringResource(R.string.close)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveFilterChips(model: AppListViewModel) {
    if (model.activeFilterIds.isEmpty() && model.collectionPackages.isEmpty() && !model.onlySystem) return
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        model.activeFilterIds.mapNotNull(io.github.samolego.canta.util.apps.Filter::fromId).forEach { filter ->
            InputChip(selected = true, onClick = { model.activeFilterIds -= filter.id },
                label = { Text(filter.nameRes?.let { stringResource(it) } ?: filter.name) },
                trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.remove_filter, filter.nameRes?.let { stringResource(it) } ?: filter.name)) })
        }
        if (model.onlySystem) InputChip(selected = true, onClick = { model.onlySystem = false }, label = { Text(stringResource(R.string.only_system)) }, trailingIcon = { Icon(Icons.Default.Close, null) })
        if (model.collectionPackages.isNotEmpty()) InputChip(selected = true, onClick = { model.collectionPackages = emptySet() }, label = { Text(stringResource(R.string.collection_filter)) }, trailingIcon = { Icon(Icons.Default.Close, null) })
    }
}
