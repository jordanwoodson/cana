package io.github.samolego.canta.ui.dialog

import io.github.samolego.canta.ui.menu.profileName

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.util.openWebLink
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.data.SettingsStore
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import io.github.samolego.canta.util.withPackageAuthentication
import io.github.samolego.canta.ui.screen.UndoBatchDialog
import io.github.samolego.canta.ui.component.InfoDisclosure
import io.github.samolego.canta.ui.component.StatusChip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ComponentsDialog(packageName: String, userId: Int, onDismiss: () -> Unit) = ComponentsDialog(packageName, userId, false, onDismiss)

@Composable
fun ComponentsDialog(packageName: String, userId: Int, embedded: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val services = CanaServices.getInstance()
    val scope = rememberCoroutineScope()
    var authorized by remember { mutableStateOf(ShizukuPermission.isCantaAuthorized()) }
    if (!authorized) {
        ShizukuRequirementDialog({ if (it) authorized = true else onDismiss() })
        return
    }
    var catalog by remember { mutableStateOf<ComponentCatalog?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val loadFailed = stringResource(R.string.components_load_failed)
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var undoReview by remember { mutableStateOf<OperationRecord?>(null) }
    var pending by remember { mutableStateOf<AppComponent?>(null) }
    var sourceDialog by remember { mutableStateOf(false) }
    LaunchedEffect(packageName, userId, revision) {
        loading = true
        loadError = null
        try { catalog = services.components.load(packageName, userId) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            LogUtils.e("Components", "Cannot load components for $packageName", e)
            loadError = loadFailed
        }
        finally { loading = false }
    }
    val content: @Composable () -> Unit = {
            LazyColumn(if (embedded) Modifier.fillMaxSize() else Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!embedded) {
                    item {
                        Column {
                            Text(packageName, style = MaterialTheme.typography.labelMedium)
                            Text(profileName(userId))
                        }
                    }
                }
                if (busy || loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
                loadError?.let { error -> item {
                    Column {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { revision++ }) { Text(stringResource(R.string.retry)) }
                    }
                } }
                val state = catalog
                if (state != null) {
                    if (!state.editable) item { Text(stringResource(R.string.component_requires_root), style = MaterialTheme.typography.bodySmall) }
                    state.trackers.error?.let { error -> item { Text(stringResource(R.string.tracker_fallback, error), style = MaterialTheme.typography.bodySmall) } }
                        items(state.components, key = { it.name.flattenToString() }) { component ->
                            val editable = !busy && !loading && loadError == null && state.editable && packageName !in services.safety.alwaysProtected
                            ListItem(modifier = Modifier.toggleable(component.enabled, enabled = editable,
                                    role = Role.Switch, onValueChange = { pending = component }),
                                headlineContent = { Text(component.name.className, style = MaterialTheme.typography.bodyMedium) },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    StatusChip(stringResource(when (component.kind) {
                                        "activity" -> R.string.component_activity
                                        "service" -> R.string.component_service
                                        "receiver" -> R.string.component_receiver
                                        else -> R.string.component_provider
                                    }), Icons.Default.Extension)
                                    if (component.trackers.isNotEmpty()) Text(component.trackers.joinToString(),
                                        color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                                    }
                                }, trailingContent = {
                                    Switch(component.enabled, onCheckedChange = null, enabled = editable)
                                })
                        }
                    item {
                    InfoDisclosure(stringResource(R.string.about_tracker_scanning)) {
                        Text(stringResource(R.string.tracker_match_description), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.tracker_active_source, state.trackers.source), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.tracker_attribution), style = MaterialTheme.typography.labelSmall)
                        Row {
                            TextButton(onClick = { context.openWebLink("https://reports.exodus-privacy.eu.org/trackers/") }) { Text("Exodus Privacy") }
                            TextButton(onClick = { context.openWebLink("https://opendatacommons.org/licenses/odbl/1-0/") }) { Text("ODbL 1.0") }
                        }
                    }
                    }
                    item { TextButton(onClick = { sourceDialog = true }) { Text(stringResource(R.string.tracker_source)) } }
                }
            }
    }
    if (embedded) content() else AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.components)) }, text = content,
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.close)) } })
    undoReview?.let { record -> UndoBatchDialog(listOf(record), { undoReview = null }) { result ->
        message = if (result.failureCount > 0 || result.unknownCount > 0) result.results.joinToString("\n") { it.message } else null
        if (result.failureCount == 0 && result.unknownCount == 0) services.uiMessages.post(
            resources.getQuantityString(R.plurals.component_undo_result, result.successCount, result.successCount))
        undoReview = null
        revision++
    } }

    pending?.let { component ->
        var assessment by remember(component) { mutableStateOf<SafetyAssessment?>(null) }
        var accepted by remember(component) { mutableStateOf(false) }
        LaunchedEffect(component) { assessment = services.safety.inspect(listOf(packageName), userId).getValue(packageName) }
        val report = assessment
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(stringResource(if (component.enabled) R.string.disable_app else R.string.enable_app)) },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text(packageName)
                    Text(profileName(userId))
                    Text(component.name.className)
                    if (report == null) LinearProgressIndicator()
                    else {
                        if (report.protected) Text(stringResource(R.string.safety_protected, packageName))
                        report.error?.let { Text(it) }
                        if (component.enabled && report.warnings.isNotEmpty()) {
                            report.warnings.forEach { Text("${it.kind}: ${it.detail}") }
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(accepted,
                                role = Role.Checkbox, onValueChange = { accepted = it }), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(accepted, onCheckedChange = null)
                                Text(stringResource(R.string.safety_acknowledge), Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = report != null && !report.protected && report.error == null &&
                    (!component.enabled || report.warnings.isEmpty() || accepted), onClick = {
                    pending = null
                    scope.launch {
                        withPackageAuthentication(context) {
                        busy = true
                        try {
                            val approved = report!!.warnings.map { it.key }.toSet()
                            val result = services.batches.run(resources.getString(R.string.components),
                                listOf(BatchItem(component.name.flattenToString(), packageName, userId, "component"))) { _, batch ->
                                services.packageOps.setComponentEnabled(component.name, userId, !component.enabled, approved, batch)
                            }
                            val record = services.history.state.first().records.lastOrNull { it.batchId == result.batchId && it.changed }
                            message = if (result.failureCount > 0 || result.unknownCount > 0) result.results.joinToString("\n") { it.message } else null
                            val feedback = when {
                                result.failureCount > 0 -> resources.getString(R.string.component_change_failed)
                                result.unknownCount > 0 -> resources.getString(R.string.component_change_verify)
                                component.enabled -> resources.getQuantityString(R.plurals.component_disabled_result,
                                    result.successCount, result.successCount)
                                else -> resources.getQuantityString(R.plurals.component_enabled_result,
                                    result.successCount, result.successCount)
                            }
                            services.uiMessages.post(feedback,
                                actionLabel = if (record != null) resources.getString(R.string.undo_action) else null,
                                onAction = if (record != null) ({ undoReview = record }) else null,
                                withDismiss = record != null)
                            revision++
                        } finally { busy = false }
                        }
                    }
                }) { Text(stringResource(R.string.ok)) }
            }, dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (sourceDialog) TrackerSourceDialog(onDismiss = { sourceDialog = false }, onSaved = { sourceDialog = false; revision++ })
}

@Composable
internal fun TrackerSourceDialog(onDismiss: () -> Unit, onSaved: () -> Unit,
    loadSource: suspend () -> String = { SettingsStore.getInstance().trackerListUrlFlow.first() },
    saveSource: suspend (String) -> Unit = { CanaServices.getInstance().trackers.setSource(it) },
) {
    var url by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var loadAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(loadAttempt) {
        if (url == null) try { url = loadSource(); error = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: e.toString() }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.tracker_source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tracker_source_description))
                OutlinedTextField(url.orEmpty(), { url = it }, enabled = !busy && url != null, label = { Text(stringResource(R.string.tracker_url)) })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy || url == null && error == null) LinearProgressIndicator()
                if (url == null && error != null) TextButton(onClick = { loadAttempt++ }) { Text(stringResource(R.string.retry)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && url != null, onClick = { scope.launch {
                busy = true
                try { saveSource(requireNotNull(url)); onSaved() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message }
                finally { busy = false }
            } }) { Text(stringResource(R.string.save)) }
        }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
