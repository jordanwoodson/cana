package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.ActionChoices
import io.github.samolego.canta.ops.SelectionSummary
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.ui.menu.profileName

@Composable
fun profileLabel(model: AppListViewModel): String = profileName(model.selectedUserId)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionActionBar(model: AppListViewModel, appType: AppsType) {
    if (model.selectedApps.isEmpty()) return
    var actions by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf(false) }
    val visible = model.appList.filter { it.isUninstalled == (appType == AppsType.UNINSTALLED) }.map { it.packageName }.toSet()
    val summary = SelectionSummary.of(model.selectedApps.keys.toSet(), visible)
    val removedTab = appType == AppsType.UNINSTALLED
    val selected = model.allApps.filter { it.packageName in model.selectedApps && it.isUninstalled == removedTab }
    val choices = ActionChoices.forSelection(selected, removedTab)
    val selectionLabel = if (summary.hidden > 0) stringResource(R.string.selection_hidden_short, summary.total, summary.hidden)
        else pluralStringResource(R.plurals.selection_selected_count, summary.total, summary.total)
    val destructivePrimary = choices.primary in setOf(PackageAction.UNINSTALL, PackageAction.REMOVE_UPDATES, PackageAction.UNINSTALL_KEEP_DATA)
    val actionColor = if (destructivePrimary) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val onActionColor = if (destructivePrimary) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    Surface(tonalElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = !model.isOperating, onClick = { model.selectedApps.clear() }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
            }
            TextButton(modifier = Modifier.weight(1f), onClick = { review = true }) {
                Text(selectionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = !model.isOperating, onClick = { model.requestAction(choices.primary) },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = actionColor, contentColor = onActionColor)) {
                        Text(stringResource(choices.primary.label), maxLines = 1)
                    }
                    IconButton(enabled = !model.isOperating && (choices.reversible.isNotEmpty() || choices.removing.isNotEmpty()),
                        onClick = { actions = true },
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 24.dp, bottomEnd = 24.dp)).background(actionColor),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = onActionColor)) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.selection_more_actions))
                    }
                }
                DropdownMenu(actions, { actions = false }) {
                    if (choices.reversible.isNotEmpty()) {
                        Text(stringResource(R.string.selection_reversible), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        choices.reversible.forEach { action -> ActionMenuItem(action) { actions = false; model.requestAction(action) } }
                    }
                    if (choices.removing.isNotEmpty()) {
                        if (choices.reversible.isNotEmpty()) HorizontalDivider()
                        Text(stringResource(R.string.selection_removes_app), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        choices.removing.forEach { action -> ActionMenuItem(action) { actions = false; model.requestAction(action) } }
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

@Composable
private fun ActionMenuItem(action: PackageAction, onClick: () -> Unit) {
    val destructive = action in setOf(PackageAction.UNINSTALL, PackageAction.REMOVE_UPDATES, PackageAction.UNINSTALL_KEEP_DATA)
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(text = { Text(stringResource(action.label)) }, onClick = onClick,
        leadingIcon = { Icon(actionIcon(action), contentDescription = null) },
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color))
}

private fun actionIcon(action: PackageAction): ImageVector = when (action) {
    PackageAction.DISABLE -> Icons.Default.Block
    PackageAction.ENABLE -> Icons.Default.CheckCircle
    PackageAction.SUSPEND -> Icons.Default.PauseCircle
    PackageAction.UNSUSPEND -> Icons.Default.PlayCircle
    PackageAction.UNINSTALL_KEEP_DATA -> Icons.Default.Inventory2
    PackageAction.UNINSTALL -> Icons.Default.Delete
    PackageAction.REINSTALL -> Icons.Default.Restore
    PackageAction.REMOVE_UPDATES -> Icons.Default.SystemUpdateAlt
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
        if (model.onlySystem) InputChip(selected = true, onClick = { model.onlySystem = false }, label = { Text(stringResource(R.string.only_system)) },
            trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.remove_filter, stringResource(R.string.only_system))) })
        if (model.collectionPackages.isNotEmpty()) InputChip(selected = true, onClick = { model.collectionPackages = emptySet() }, label = { Text(stringResource(R.string.collection_filter)) },
            trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.remove_filter, stringResource(R.string.collection_filter))) })
    }
}
