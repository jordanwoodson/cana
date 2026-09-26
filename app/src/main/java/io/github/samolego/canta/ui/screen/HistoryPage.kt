package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.R
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.ui.component.IconClickButton
import io.github.samolego.canta.ui.component.RestoreExportButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryPage(onNavigateBack: () -> Unit) {
    val services = CanaServices.getInstance()
    val records by services.history.records.collectAsStateWithLifecycle(emptyList())
    var pending by remember { mutableStateOf<List<OperationRecord>?>(null) }
    var result by remember { mutableStateOf<BatchResult?>(null) }
    val last = remember(records) { ManagementPolicy.lastBatch(records) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.history)) },
        navigationIcon = { IconClickButton(onClick = onNavigateBack, icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
        actions = { RestoreExportButton() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Button(enabled = last.isNotEmpty(), onClick = { pending = last }) { Text(stringResource(R.string.undo_last_batch)) } }
            result?.let { batch -> item {
                Text(stringResource(R.string.privacy_results, batch.successCount, batch.failureCount))
                batch.results.forEach { Text(it.message, style = MaterialTheme.typography.bodySmall) }
            } }
            if (records.isEmpty()) item { Text(stringResource(R.string.history_empty)) }
            items(records.asReversed(), key = { it.id }) { record ->
                var expanded by remember(record.id) { mutableStateOf(false) }
                ElevatedCard(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(record.packageName.ifBlank { stringResource(R.string.device_wide) }, style = MaterialTheme.typography.titleSmall)
                        Text("${record.action} · " + if (record.action == "system") stringResource(R.string.device_wide) else stringResource(R.string.action_user, record.userId))
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.timestampMs)), style = MaterialTheme.typography.bodySmall)
                        Text(if (!record.completed) stringResource(R.string.history_pending) else record.resultMessage,
                            color = if (record.completed && !record.success) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        if (record.undoOf.isNotBlank()) Text(stringResource(R.string.history_undo_record), style = MaterialTheme.typography.labelSmall)
                        if (expanded) {
                            Text(stringResource(R.string.history_before, record.previousState), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.history_after, record.afterState), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    pending?.let { captured -> UndoBatchDialog(captured, { pending = null }) { result = it; pending = null } }
}

@Composable
private fun UndoBatchDialog(records: List<OperationRecord>, onDismiss: () -> Unit, onResult: (BatchResult) -> Unit) {
    val services = CanaServices.getInstance()
    val scope = rememberCoroutineScope()
    var assessments by remember { mutableStateOf<Map<Int, Map<String, SafetyAssessment>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var accepted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(records) {
        try {
            assessments = records.filter { record -> RestoreScript.plan(record).commands.any {
                it.getOrNull(1) in setOf("disable", "disable-user", "disable-until-used", "uninstall", "suspend")
            } }.groupBy { it.userId }.mapValues { (user, entries) ->
                services.safety.inspect(entries.map { it.packageName }.distinct(), user, removing = true)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: e.toString() }
    }
    val warnings = assessments?.values.orEmpty().flatMap { it.values }.flatMap { it.warnings }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.undo_last_batch)) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.undo_batch_description))
            records.forEach { record ->
                Text("${record.packageName} · ${record.action} · " + stringResource(R.string.action_user, record.userId))
                RestoreScript.plan(record).notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            warnings.forEach { Text("${it.kind}: ${it.detail}") }
            if (warnings.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(accepted, { accepted = it }); Text(stringResource(R.string.safety_acknowledge))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy || assessments == null && error == null) LinearProgressIndicator()
        } }, confirmButton = {
            TextButton(enabled = !busy && assessments != null && (warnings.isEmpty() || accepted), onClick = {
                busy = true
                val approved = assessments!!.mapValues { (_, reports) -> reports.values.flatMap { it.warnings }.map { it.key }.toSet() }
                scope.launch { try { onResult(services.undo.undo(records, approved)) } finally { busy = false } }
            }) { Text(stringResource(R.string.undo_action)) }
        }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
