package io.github.samolego.canta.ui.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.R
import io.github.samolego.canta.data.SavedView
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.ui.component.ChoiceOption
import io.github.samolego.canta.ui.component.ChoiceRow
import io.github.samolego.canta.ui.dialog.ExplainBadgesDialog
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.AppSort
import io.github.samolego.canta.util.apps.Filter
import io.github.samolego.canta.util.apps.FilterGroup
import io.github.samolego.canta.util.apps.AppScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersMenu(showMenu: Boolean, onDismiss: () -> Unit, appListViewModel: AppListViewModel, appType: AppsType) {
    if (!showMenu) return
    val model = appListViewModel
    val store = remember { CanaServices.getInstance().savedViews }
    var viewsState by remember { mutableStateOf(io.github.samolego.canta.data.SavedViewsState(loading = true)) }
    var readRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(store, readRevision) { store.state.collect { viewsState = it } }
    val scope = rememberCoroutineScope()
    var save by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SavedView?>(null) }
    var explainBadges by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.filters_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.saved_views), style = MaterialTheme.typography.titleSmall)
            val available = viewsState.views.filter { it.appliesTo(model.selectedUserId) }
            if (viewsState.loading) LinearProgressIndicator()
            else if (viewsState.unavailable) {
                Text(stringResource(R.string.saved_views_unavailable), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { readRevision++ }) { Text(stringResource(R.string.inventory_retry)) }
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(enabled = !viewsState.unavailable, onClick = { save = true },
                        label = { Text(stringResource(R.string.save_current_view)) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) })
                    available.forEach { view ->
                        FilterChip(selected = false, onClick = { model.applySavedView(view); onDismiss() }, label = { Text(view.name) })
                        IconButton(onClick = { deleteTarget = view }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Delete, stringResource(R.string.saved_view_delete, view.name))
                        }
                    }
                }
                if (available.isEmpty()) Text(stringResource(R.string.saved_views_empty), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.filter_scope), style = MaterialTheme.typography.titleSmall)
            ChoiceRow(
                options = listOf(
                    ChoiceOption(AppScope.ALL, stringResource(R.string.scope_all), "scope-all"),
                    ChoiceOption(AppScope.SYSTEM, stringResource(R.string.scope_system), "scope-system"),
                    ChoiceOption(AppScope.USER, stringResource(R.string.scope_user), "scope-user"),
                ), selected = model.scope, onSelect = model::setScope,
            )
            Text(stringResource(R.string.filter_sort), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSort.entries.filter { it != AppSort.LAST_USED || model.usageAvailable }.forEach { sort ->
                    val shortTitle = when (sort) {
                        AppSort.NAME -> R.string.sort_short_name
                        AppSort.SIZE -> R.string.sort_short_size
                        AppSort.LAST_USED -> R.string.sort_short_last_used
                        AppSort.TRACKERS -> R.string.sort_short_trackers
                    }
                    FilterChip(selected = model.sortOrder == sort, onClick = { model.sortOrder = sort }, label = { Text(stringResource(shortTitle)) })
                }
            }
            FilterGroup.entries.filter { it != FilterGroup.SCOPE }.forEach { group ->
                Text(stringResource(when (group) {
                    FilterGroup.SCOPE -> R.string.filter_scope
                    FilterGroup.STATE -> R.string.filter_state
                    FilterGroup.RISK -> R.string.filter_risk
                    FilterGroup.CATEGORY -> R.string.filter_category
                    FilterGroup.PRIVACY -> R.string.filter_privacy
                }), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filter.availableFilters.filter { it != Filter.any && it.group == group && (it != Filter.unused || model.usageAvailable) }.forEach { filter ->
                        FilterChip(selected = filter.id in model.activeFilterIds, onClick = { model.toggleFilter(filter) },
                            label = { Text(filter.nameRes?.let { stringResource(it) } ?: filter.name) })
                    }
                }
                if (group == FilterGroup.PRIVACY) io.github.samolego.canta.ui.component.TrackerScanControls(model)
            }
            TextButton(onClick = { explainBadges = true }) { Text(stringResource(R.string.risk_levels_explain)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = model::clearFilters) { Text(stringResource(R.string.clear)) }
            val count = model.appList.count { it.isUninstalled == (appType == AppsType.UNINSTALLED) }
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(pluralStringResource(R.plurals.show_app_count, count, count))
            }
        }
        }
    }
    if (explainBadges) ExplainBadgesDialog { explainBadges = false }
    deleteTarget?.let { view ->
        AlertDialog(onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_saved_view_title)) },
            text = { Text(stringResource(R.string.delete_saved_view_message, view.name)) },
            confirmButton = { TextButton(onClick = {
                deleteTarget = null
                scope.launch { try { store.delete(view.id) } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message } }
            }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (save) {
        var name by remember { mutableStateOf("") }
        var scoped by remember { mutableStateOf(true) }
        var collection by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { if (!busy) save = false }, title = { Text(stringResource(R.string.save_view)) }, text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.saved_view_name)) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(scoped, { scoped = it }); Text(stringResource(R.string.saved_view_profile)) }
                if (model.selectedApps.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(collection, { collection = it }); Text(stringResource(R.string.saved_view_collection))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                val view = SavedView(UUID.randomUUID().toString(), name.trim(), if (scoped) model.selectedUserId else null,
                    model.searchQuery, model.activeFilterIds, model.onlySystem, model.sortOrder.name,
                    if (collection) model.selectedApps.keys.toSet() else model.collectionPackages)
                busy = true
                scope.launch { try { store.save(view); save = false } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message } finally { busy = false } }
            }) { Text(stringResource(R.string.save)) }
        }, dismissButton = { TextButton(enabled = !busy, onClick = { save = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
