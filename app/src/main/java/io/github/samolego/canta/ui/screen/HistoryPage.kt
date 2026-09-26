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
import androidx.compose.ui.platform.LocalContext
import io.github.samolego.canta.util.withPackageAuthentication
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.ui.component.IconClickButton
import io.github.samolego.canta.ui.component.RestoreExportButton
import io.github.samolego.canta.ui.menu.displayName
import io.github.samolego.canta.util.apps.UserProfile
import io.github.samolego.canta.util.shizuku.ShizukuUserUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryPage(onNavigateBack: () -> Unit) {
    val services = CanaServices.getInstance()
    val historyState by services.history.state.collectAsStateWithLifecycle(io.github.samolego.canta.data.HistoryReadState())
    val records = historyState.records
    val quarantined = historyState.quarantinedRecords
    var profiles by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var showQuarantined by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try { profiles = withContext(Dispatchers.IO) { ShizukuUserUtils.getUsers() } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* User IDs remain available when the service is disconnected. */ }
    }
    val stored by services.batchStore.state.collectAsStateWithLifecycle(io.github.samolego.canta.data.BatchReadState())
    val batches = stored.batches
    val active by services.batches.active.collectAsStateWithLifecycle()
    val groups = remember(records, batches) { HistoryTimeline.group(records, batches) }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<OperationRecord>?>(null) }
    var result by remember { mutableStateOf<BatchResult?>(null) }
    val last = remember(records) { ManagementPolicy.lastBatch(records) }
    val recovered = remember(records) { records.filter { it.undoOf.isNotEmpty() && it.completed && (it.success || it.recoveryComplete) }.map { it.undoOf }.toSet() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.history)) },
        navigationIcon = { IconClickButton(onClick = onNavigateBack, icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
        actions = { RestoreExportButton() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Button(enabled = active == null && last.isNotEmpty() && batches.none { !it.completed && it.id == last.firstOrNull()?.batchId }, onClick = { pending = last }) { Text(stringResource(R.string.undo_last_batch)) } }
            result?.let { batch -> item {
                Text(stringResource(R.string.batch_outcome_counts, batch.successCount, batch.failureCount, batch.skippedCount, batch.unknownCount))
                batch.results.forEach { Text(it.message, style = MaterialTheme.typography.bodySmall) }
            } }
            if (stored.unavailable) item { Text(stringResource(R.string.batch_store_unavailable), color = MaterialTheme.colorScheme.error) }
            if (historyState.unavailable) item { Text(stringResource(R.string.history_store_unavailable), color = MaterialTheme.colorScheme.error) }
            if (quarantined.isNotEmpty()) item {
                Text(stringResource(R.string.history_quarantined, quarantined.size))
                TextButton(onClick = { showQuarantined = !showQuarantined }) { Text(stringResource(if (showQuarantined) R.string.close else R.string.review_actions)) }
            }
            if (showQuarantined) items(quarantined, key = { "quarantined:${it.id}" }) { HistoryRecordContent(it, profiles) }
            if (groups.isEmpty() && !historyState.unavailable) item { Text(stringResource(R.string.history_empty)) }
            items(groups, key = { it.id }) { group ->
                var expanded by remember(group.id) { mutableStateOf(false) }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val plan = group.plan
                        Text(plan?.title ?: actionLabel(group.records.firstOrNull()?.action.orEmpty()), style = MaterialTheme.typography.titleMedium)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(group.timestamp)), style = MaterialTheme.typography.bodySmall)
                        val changes = group.records.filter { it.undoOf.isEmpty() && (it.changed || !it.completed) }
                        if (changes.isNotEmpty()) Text(stringResource(R.string.history_recovery_counts,
                            changes.count { it.id in recovered || it.recoveryComplete },
                            changes.count { it.id !in recovered && !it.recoveryComplete }), style = MaterialTheme.typography.bodySmall)
                        if (plan?.interrupted == true) {
                            Text(stringResource(R.string.batch_interrupted_description))
                            if (!plan.reviewAcknowledged) TextButton(onClick = { scope.launch { services.batchStore.reviewed(plan.id) } }) {
                                Text(stringResource(R.string.batch_reviewed))
                            }
                        }
                        if (plan != null) {
                            Text(stringResource(R.string.batch_outcome_counts,
                                plan.itemsList.count { it.status == "completed" && it.success && !it.skipped },
                                plan.itemsList.count { it.status == "completed" && !it.success },
                                plan.itemsList.count { it.skipped || it.status == "not_started" },
                                plan.itemsList.count { it.status == "pending" || it.status == "interrupted" }))
                            if (!plan.completed) Text(stringResource(R.string.batch_running))
                        }
                        TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.close else R.string.review_actions)) }
                        if (expanded) {
                            group.records.forEach { record -> HistoryRecordContent(record, profiles) }
                            plan?.itemsList?.filter { item -> group.records.none {
                                it.packageName == item.packageName && it.userId == item.userId && (it.action == item.action || item.action.startsWith("undo_"))
                            } }?.forEach { item ->
                                HorizontalDivider()
                                Text(item.packageName.ifBlank { stringResource(R.string.device_wide) })
                                Text(actionLabel(item.action) + " · " + historyProfile(item.userId, profiles), style = MaterialTheme.typography.bodySmall)
                                Text(when (item.status) {
                                    "queued" -> stringResource(R.string.batch_queued)
                                    "running" -> stringResource(R.string.batch_running)
                                    "pending", "interrupted" -> stringResource(R.string.history_pending)
                                    "not_started", "cancelled" -> stringResource(R.string.batch_stopped_item)
                                    else -> item.message
                                })
                            }
                        }
                    }
                }
            }
        }
    }
    pending?.let { captured -> UndoBatchDialog(captured, { pending = null }) { result = it; pending = null } }
}

@Composable
private fun HistoryRecordContent(record: OperationRecord, profiles: List<UserProfile>) {
    var diagnostics by remember(record.id) { mutableStateOf(false) }
    HorizontalDivider()
    Text(record.packageName.ifBlank { stringResource(R.string.device_wide) }, style = MaterialTheme.typography.titleSmall)
    Text(actionLabel(record.action) + " · " + if (record.action == "system") stringResource(R.string.device_wide) else historyProfile(record.userId, profiles))
    Text(if (!record.completed) stringResource(R.string.history_pending) else record.resultMessage,
        color = if (record.completed && !record.success) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    if (record.recoveryComplete) Text(stringResource(R.string.history_recovery_complete), style = MaterialTheme.typography.labelMedium)
    if (record.undoOf.isNotBlank()) Text(stringResource(R.string.history_undo_record), style = MaterialTheme.typography.labelMedium)
    TextButton(onClick = { diagnostics = !diagnostics }) { Text(stringResource(R.string.history_diagnostics)) }
    if (diagnostics) {
        Text(stringResource(R.string.history_before, record.previousState), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.history_after, record.afterState), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun historyProfile(userId: Int, profiles: List<UserProfile>): String {
    val profile = profiles.find { it.id == userId }
    return (profile?.let { it.displayName() + " · " } ?: "") + stringResource(R.string.action_user, userId)
}

@Composable
internal fun actionLabel(action: String): String {
    if (action.startsWith("undo_")) return stringResource(R.string.undo_action) + " · " + actionLabel(action.removePrefix("undo_"))
    val privacy = PrivacyAction.entries.find { it.key == action }
    if (privacy != null) return stringResource(privacy.title)
    val resource = when (action) {
        "uninstall" -> R.string.uninstall
        "uninstall_keep_data" -> R.string.uninstall_keep_data
        "reinstall", "install" -> R.string.reinstall
        "remove_updates" -> R.string.remove_updates
        "disable" -> R.string.disable_app
        "enable" -> R.string.enable_app
        "suspend" -> R.string.suspend_app
        "unsuspend" -> R.string.unsuspend_app
        "component" -> R.string.components
        "network_forget" -> R.string.history_forget_network
        "metered_forget" -> R.string.history_forget_metered
        "system" -> R.string.system_controls
        else -> R.string.operation_results
    }
    return stringResource(resource)
}

@Composable
internal fun UndoBatchDialog(records: List<OperationRecord>, onDismiss: () -> Unit, onResult: (BatchResult) -> Unit) {
    val context = LocalContext.current
    val services = CanaServices.getInstance()
    val scope = rememberCoroutineScope()
    val active by services.batches.active.collectAsStateWithLifecycle()
    var assessments by remember { mutableStateOf<Map<Int, Map<String, SafetyAssessment>>?>(null) }
    var privacyApprovals by remember { mutableStateOf<Map<Int, Set<String>>>(emptyMap()) }
    var sharedPeers by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var accepted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(records) {
        try {
            val reports = records.filter { record -> RestoreScript.plan(record).commands.any {
                it.getOrNull(1) in setOf("disable", "disable-user", "disable-until-used", "uninstall", "suspend")
            } }.groupBy { it.userId }.mapValues { (user, entries) ->
                services.safety.inspect(entries.map { it.packageName }.distinct(), user, removing = true)
            }.toMutableMap()
            val consent = mutableMapOf<Int, Set<String>>()
            val peers = mutableListOf<String>()
            for (record in records) {
                val action = PrivacyAction.entries.find { it.key == record.action } ?: continue
                val prior = SnapshotState.read(record.previousState)
                val current = services.privacy.snapshot(record.packageName, record.userId, action)
                if (PrivacyReviewPolicy.undoNeedsConsent(action.key, prior, current)) {
                    val review = services.privacy.assess(record.packageName, record.userId)
                    val combined = reports[record.userId].orEmpty().toMutableMap()
                    review.forEach { (pkg, fresh) ->
                        val previous = combined[pkg]
                        combined[pkg] = if (previous == null) fresh else SafetyAssessment(
                            previous.protected || fresh.protected,
                            (previous.warnings + fresh.warnings).distinctBy { it.key }, previous.error ?: fresh.error)
                    }
                    reports[record.userId] = combined
                    consent[record.userId] = consent[record.userId].orEmpty() + PrivacyOps.approvalKeys(record.packageName, record.userId, review)
                    if (review.size > 1) peers += review.keys
                }
            }
            privacyApprovals = consent
            sharedPeers = peers.distinct()
            assessments = reports
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: e.toString() }
    }
    val warnings = assessments?.values.orEmpty().flatMap { it.values }.flatMap { it.warnings }
    val blocked = assessments?.values.orEmpty().flatMap { it.values }.any { it.protected || it.error != null }
    val needsAcknowledgment = warnings.isNotEmpty() || sharedPeers.isNotEmpty()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.undo_last_batch)) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.undo_batch_description))
            records.forEach { record ->
                Text("${record.packageName} · " + actionLabel(record.action) + " · " + stringResource(R.string.action_user, record.userId))
                if (record.action == "component") runCatching { SnapshotState.read(record.previousState).optString("component") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                RestoreScript.plan(record).notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            warnings.forEach { Text("${it.kind}: ${it.detail}") }
            assessments?.values.orEmpty().forEach { reports -> reports.forEach { (pkg, report) ->
                if (report.protected) Text(stringResource(R.string.safety_protected, pkg))
                report.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } }
            if (sharedPeers.isNotEmpty()) Text(stringResource(R.string.privacy_shared_uid, sharedPeers.joinToString()))
            if (needsAcknowledgment) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(accepted, { accepted = it }); Text(stringResource(R.string.safety_acknowledge))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy || assessments == null && error == null) LinearProgressIndicator()
        } }, confirmButton = {
            TextButton(enabled = !busy && active == null && records.isNotEmpty() && assessments != null && !blocked && (!needsAcknowledgment || accepted), onClick = {
                busy = true
                val approved = assessments!!.mapValues { (user, reports) -> reports.values.flatMap { it.warnings }.map { it.key }.toSet() + privacyApprovals[user].orEmpty() }
                scope.launch { try { withPackageAuthentication(context) { onResult(services.undo.undo(records, approved)) } } finally { busy = false } }
            }) { Text(stringResource(R.string.undo_action)) }
        }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
