package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.data.SettingsStore
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ComponentsDialog(packageName: String, userId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val services = CanaServices.getInstance()
    val scope = rememberCoroutineScope()
    var authorized by remember { mutableStateOf(ShizukuPermission.isCantaAuthorized()) }
    if (!authorized) {
        ShizukuRequirementDialog({ if (it) authorized = true else onDismiss() },
            ShizukuPermission.checkShizukuActive(context.packageManager))
        return
    }
    var catalog by remember { mutableStateOf<ComponentCatalog?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var lastRecord by remember { mutableStateOf<OperationRecord?>(null) }
    var pending by remember { mutableStateOf<AppComponent?>(null) }
    var sourceDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(packageName, userId, revision) {
        try { catalog = services.components.load(packageName, userId) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.message }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.components)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(packageName, style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.action_user, userId))
                if (busy || catalog == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                val state = catalog
                if (state != null) {
                    if (!state.editable) Text(stringResource(R.string.component_requires_root), style = MaterialTheme.typography.bodySmall)
                    state.trackers.error?.let { Text(stringResource(R.string.tracker_fallback, it), style = MaterialTheme.typography.bodySmall) }
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(state.components, key = { it.name.flattenToString() }) { component ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(component.name.className, style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(when (component.kind) {
                                        "activity" -> R.string.component_activity
                                        "service" -> R.string.component_service
                                        "receiver" -> R.string.component_receiver
                                        else -> R.string.component_provider
                                    }), style = MaterialTheme.typography.labelSmall)
                                    if (component.trackers.isNotEmpty()) Text(component.trackers.joinToString(),
                                        color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                                }
                                Switch(component.enabled, { pending = component }, enabled = !busy && state.editable && packageName !in services.safety.alwaysProtected)
                            }
                        }
                    }
                    Text(stringResource(R.string.tracker_match_description), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.tracker_active_source, state.trackers.source), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.tracker_attribution), style = MaterialTheme.typography.labelSmall)
                    Row {
                        TextButton(onClick = { uriHandler.openUri("https://reports.exodus-privacy.eu.org/trackers/") }) { Text("Exodus Privacy") }
                        TextButton(onClick = { uriHandler.openUri("https://opendatacommons.org/licenses/odbl/1-0/") }) { Text("ODbL 1.0") }
                    }
                    TextButton(onClick = { sourceDialog = true }) { Text(stringResource(R.string.tracker_source)) }
                }
                lastRecord?.let { record ->
                    TextButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val result = services.packageOps.undo(record)
                                message = result.message
                                if (result.success) lastRecord = null
                                revision++
                            } finally { busy = false }
                        }
                    }) { Text(stringResource(R.string.undo_action)) }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    pending?.let { component ->
        var assessment by remember(component) { mutableStateOf<SafetyAssessment?>(null) }
        var accepted by remember(component) { mutableStateOf(false) }
        LaunchedEffect(component) { assessment = services.safety.inspect(listOf(packageName), userId).getValue(packageName) }
        val report = assessment
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(stringResource(if (component.enabled) R.string.disable_app else R.string.enable_app)) },
            text = {
                Column {
                    Text(component.name.className)
                    if (report == null) LinearProgressIndicator()
                    else {
                        if (report.protected) Text(stringResource(R.string.safety_protected, packageName))
                        report.error?.let { Text(it) }
                        if (component.enabled && report.warnings.isNotEmpty()) {
                            report.warnings.forEach { Text("${it.kind}: ${it.detail}") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(accepted, { accepted = it })
                                Text(stringResource(R.string.safety_acknowledge))
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
                        busy = true
                        try {
                            val batch = UUID.randomUUID().toString()
                            val result = services.packageOps.setComponentEnabled(component.name, userId, !component.enabled,
                                report!!.warnings.map { it.key }.toSet(), batch)
                            message = result.message
                            lastRecord = services.history.records.first().lastOrNull { it.batchId == batch && it.changed }
                            revision++
                        } finally { busy = false }
                    }
                }) { Text(stringResource(R.string.ok)) }
            }, dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (sourceDialog) TrackerSourceDialog(onDismiss = { sourceDialog = false }, onSaved = { sourceDialog = false; revision++ })
}

@Composable
private fun TrackerSourceDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { url = SettingsStore.getInstance().trackerListUrlFlow.first() }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.tracker_source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tracker_source_description))
                OutlinedTextField(url, { url = it }, enabled = !busy, label = { Text(stringResource(R.string.tracker_url)) })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { scope.launch {
                busy = true
                try { CanaServices.getInstance().trackers.setSource(url); onSaved() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message }
                finally { busy = false }
            } }) { Text(stringResource(R.string.save)) }
        }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
