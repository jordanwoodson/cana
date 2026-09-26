package io.github.samolego.canta.ui.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.R
import io.github.samolego.canta.data.SavedView
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.AppSort
import io.github.samolego.canta.util.apps.Filter
import io.github.samolego.canta.util.apps.FilterGroup
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersMenu(showMenu: Boolean, onDismiss: () -> Unit, appListViewModel: AppListViewModel) {
    if (!showMenu) return
    val model = appListViewModel
    val store = remember { CanaServices.getInstance().savedViews }
    var viewsState by remember { mutableStateOf(io.github.samolego.canta.data.SavedViewsState(loading = true)) }
    var readRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(store, readRevision) { store.state.collect { viewsState = it } }
    val scope = rememberCoroutineScope()
    var save by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.filters_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.filter_sort), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSort.entries.filter { it != AppSort.LAST_USED || model.usageAvailable }.forEach { sort ->
                    FilterChip(selected = model.sortOrder == sort, onClick = { model.sortOrder = sort }, label = { Text(stringResource(sort.title)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(model.onlySystem, {
                    model.onlySystem = it
                    if (it) model.activeFilterIds -= Filter.user.id
                })
                Text(stringResource(R.string.only_system))
            }
            FilterGroup.entries.forEach { group ->
                Text(stringResource(when (group) {
                    FilterGroup.STATE -> R.string.filter_state
                    FilterGroup.RISK -> R.string.filter_risk
                    FilterGroup.CATEGORY -> R.string.filter_category
                }), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filter.availableFilters.filter { it != Filter.any && it.group == group && (it != Filter.unused || model.usageAvailable) }.forEach { filter ->
                        FilterChip(selected = filter.id in model.activeFilterIds, onClick = { model.toggleFilter(filter) },
                            label = { Text(filter.nameRes?.let { stringResource(it) } ?: filter.name) })
                    }
                }
            }
            Row {
                TextButton(onClick = model::clearFilters) { Text(stringResource(R.string.clear_filters)) }
                TextButton(enabled = !viewsState.loading && !viewsState.unavailable, onClick = { save = true }) { Text(stringResource(R.string.save_view)) }
            }
            HorizontalDivider()
            Text(stringResource(R.string.saved_views), style = MaterialTheme.typography.titleMedium)
            val available = viewsState.views.filter { it.appliesTo(model.selectedUserId) }
            if (viewsState.loading) LinearProgressIndicator()
            else if (viewsState.unavailable) {
                Text(stringResource(R.string.saved_views_unavailable), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { readRevision++ }) { Text(stringResource(R.string.inventory_retry)) }
            } else if (available.isEmpty()) Text(stringResource(R.string.saved_views_empty))
            available.forEach { view ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(modifier = Modifier.weight(1f), onClick = { model.applySavedView(view); onDismiss() }) { Text(view.name) }
                    IconButton(onClick = { scope.launch { try { store.delete(view.id) } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message } } }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.saved_view_delete, view.name))
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
        }
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
