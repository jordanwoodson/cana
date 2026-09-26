package io.github.samolego.canta.ui.dialog

import android.text.format.Formatter.formatFileSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.BatchResult
import io.github.samolego.canta.ops.UpdateImpact
import io.github.samolego.canta.ops.SafetyAssessment
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.ui.viewmodel.PackageActionRequest
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import io.github.samolego.canta.util.withPackageAuthentication
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Requests contain immutable package/profile snapshots, including while authorization is pending. */
@Composable
fun PackageActionDialogs(model: AppListViewModel, settings: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf<Pair<PackageAction, BatchResult>?>(null) }
    var undoRecords by remember { mutableStateOf<List<io.github.samolego.canta.data.proto.OperationRecord>?>(null) }
    val request = model.pendingAction
    if (request != null) key(request) {
        var authorized by remember { mutableStateOf(ShizukuPermission.isCantaAuthorized()) }
        if (!authorized) {
            ShizukuRequirementDialog(
                shizukuStatus = ShizukuPermission.checkShizukuActive(context.packageManager),
                onClose = { if (it) authorized = true else model.pendingAction = null },
            )
        } else {
            PackageActionConfirmation(request, model,
                onDismiss = { model.pendingAction = null },
                onAgree = { included, reset, approvals, warnings ->
                    model.pendingAction = null
                    val run = {
                        scope.launch { withPackageAuthentication(context) {
                            val batch = model.processRequest(context, request, included, reset, approvals, warnings)
                            if (batch.failureCount > 0 || batch.unknownCount > 0 || request.action == PackageAction.REMOVE_UPDATES || !settings.hideSuccessDialog.value) {
                                outcome = request.action to batch
                            }
                        } }
                        Unit
                    }
                    run()
                },
            )
        }
    }
    outcome?.let { (action, batch) ->
        AlertDialog(onDismissRequest = { outcome = null },
            title = { Text(stringResource(R.string.operation_results)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.batch_outcome_counts, batch.successCount, batch.failureCount, batch.skippedCount, batch.unknownCount))
                    if (action == PackageAction.REMOVE_UPDATES) Text(stringResource(R.string.update_bytes_freed, formatFileSize(context, batch.freedBytes)))
                    batch.results.filter { !it.success }.forEach { Text(it.message) }
                }
            },
            confirmButton = { TextButton(onClick = { outcome = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = {
                batch.batchId?.let { id ->
                    TextButton(enabled = !model.isOperating, onClick = {
                        scope.launch {
                            undoRecords = io.github.samolego.canta.ops.CanaServices.getInstance().history.records.first()
                                .filter { it.batchId == id && it.changed }.asReversed()
                            outcome = null
                        }
                    }) { Text(stringResource(R.string.undo_action)) }
                }
            })
    }
    undoRecords?.let { records -> io.github.samolego.canta.ui.screen.UndoBatchDialog(records,
        { undoRecords = null }) { result -> outcome = PackageAction.REINSTALL to result; undoRecords = null } }
}

@Composable
internal fun PackageActionConfirmation(
    request: PackageActionRequest,
    model: AppListViewModel,
    confirmUninstall: Boolean = true,
    onDismiss: () -> Unit,
    onAgree: (Set<String>, Boolean, Map<String, Set<Int>>, Map<String, Set<String>>) -> Unit,
) {
    val context = LocalContext.current
    val unavailableMessage = stringResource(R.string.inventory_unavailable)
    var impacts by remember { mutableStateOf<List<UpdateImpact>?>(null) }
    var included by remember { mutableStateOf(request.apps.map { it.packageName }.toSet()) }
    var reset by remember { mutableStateOf(false) }
    var safety by remember { mutableStateOf<Map<String, SafetyAssessment>>(emptyMap()) }
    var warningsAccepted by remember(included, reset) { mutableStateOf(false) }
    var inspectionError by remember { mutableStateOf<String?>(null) }
    val cleanup = request.action == PackageAction.REMOVE_UPDATES
    LaunchedEffect(request) {
        try {
        val inspected = if (request.action == PackageAction.REINSTALL) emptyList() else model.inspectUpdates(request)
        safety = if (request.action == PackageAction.REINSTALL) emptyMap() else model.inspectSafety(request)
        impacts = inspected
        if (cleanup) included = inspected.filter { it.updated && it.error == null && it.otherInstalledProfiles.isEmpty() }.map { it.packageName }.toSet()

        included = included.filter { pkg -> safety[pkg]?.protected != true && safety[pkg]?.error == null && inspected.none { it.packageName == pkg && it.error != null } }.toSet()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { inspectionError = e.message ?: unavailableMessage }
    }
    val checked = impacts
    val warnings = if (cleanup) emptyMap() else safety.filter { it.key in included && it.value.warnings.isNotEmpty() }
    val title = request.action.label
    val updateConsent = (cleanup || reset) && checked.orEmpty().any { it.packageName in included && it.otherInstalledProfiles.isNotEmpty() }
    val needsConsent = warnings.isNotEmpty() || updateConsent
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_title)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.action_user, request.userId))
                Text(stringResource(R.string.plan_summary, request.requestedPackages.size, included.size,
                    request.requestedPackages.size - included.size))
                Text(stringResource(when (request.action) {
                    PackageAction.UNINSTALL -> R.string.plan_remove_data
                    PackageAction.UNINSTALL_KEEP_DATA -> R.string.plan_keep_data
                    PackageAction.REINSTALL -> R.string.plan_restore
                    PackageAction.REMOVE_UPDATES -> R.string.plan_update_loss
                    else -> R.string.plan_reversible
                }))
                inspectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (checked == null && inspectionError == null) {
                    Text(stringResource(R.string.checking_update_profiles))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    request.requestedPackages.forEach { pkg ->
                        val app = request.apps.find { it.packageName == pkg }
                        val assessment = safety[pkg]
                        val eligible = app != null && assessment?.protected != true && assessment?.error == null && checked.orEmpty().none { it.packageName == pkg && it.error != null } &&
                            (!cleanup || checked.orEmpty().any { it.packageName == pkg && it.updated && it.error == null })
                        Row(Modifier.fillMaxWidth().toggleable(pkg in included, enabled = eligible, role = Role.Checkbox,
                            onValueChange = { included = if (it) included + pkg else included - pkg }), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(pkg in included, enabled = eligible, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(app?.name ?: pkg, style = MaterialTheme.typography.bodyMedium)
                                if (app != null) Text(pkg, style = MaterialTheme.typography.bodySmall)
                                if (app == null) Text(stringResource(R.string.plan_unavailable), style = MaterialTheme.typography.bodySmall)
                                else if (pkg !in included) Text(stringResource(R.string.plan_excluded), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (included.isEmpty()) Text(stringResource(R.string.plan_empty))
                    checked.orEmpty().filter { it.error != null }.forEach { Text("${it.packageName}: ${it.error}", color = MaterialTheme.colorScheme.error) }
                    safety.filterValues { it.protected || it.error != null }.forEach { (name, assessment) ->
                        Text(assessment.error ?: stringResource(R.string.safety_protected, name), color = MaterialTheme.colorScheme.error)
                    }
                    warnings.forEach { (name, assessment) ->
                        Text(name, style = MaterialTheme.typography.labelLarge)
                        assessment.warnings.forEach { warning ->
                            Text(stringResource(when (warning.kind) {
                                "role" -> R.string.safety_role
                                "keyboard" -> R.string.safety_keyboard
                                "admin" -> R.string.safety_admin
                                else -> R.string.safety_dependent
                            }, warning.detail))
                        }
                    }
                    if (needsConsent) {
                        Row(Modifier.fillMaxWidth().clickable { warningsAccepted = !warningsAccepted }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(warningsAccepted, { warningsAccepted = it })
                            Text(stringResource(R.string.safety_acknowledge))
                        }
                    }
                    if (checked.orEmpty().any { it.updated }) {
                        Text(stringResource(R.string.update_global_warning))
                        Text(stringResource(R.string.update_space_estimate,
                            formatFileSize(context, checked.orEmpty().filter { !cleanup || it.packageName in included }.sumOf { it.reclaimableBytes })))
                        if (!cleanup) {
                            Row(Modifier.fillMaxWidth().clickable { reset = !reset }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = reset, onCheckedChange = { reset = it })
                                Text(stringResource(R.string.reset_to_factory_version))
                            }
                        }
                        checked.orEmpty().filter { it.updated }.forEach { impact ->
                            Column {
                                if (impact.otherInstalledProfiles.isNotEmpty()) {
                                    Text(impact.packageName, style = MaterialTheme.typography.labelMedium)
                                    Text(stringResource(if (cleanup) R.string.update_shared_warning else R.string.update_shared_uninstall_warning,
                                        impact.otherInstalledProfiles.joinToString { "${it.name ?: it.kind.name} (${it.id})" }),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else if (cleanup && checked.orEmpty().none { it.error != null }) Text(stringResource(R.string.no_leftover_updates))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = checked != null && checked.none { it.packageName in included && it.error != null } && included.isNotEmpty() &&
                (!needsConsent || warningsAccepted), onClick = {
                onAgree(included, reset, checked.orEmpty().associate { impact ->
                    impact.packageName to impact.otherInstalledProfiles.map { it.id }.toSet()
                }, warnings.mapValues { (_, report) -> report.warnings.map { it.key }.toSet() })
            }) { Text(stringResource(R.string.plan_execute)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
