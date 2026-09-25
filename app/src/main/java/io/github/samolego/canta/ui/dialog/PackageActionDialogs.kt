package io.github.samolego.canta.ui.dialog

import android.text.format.Formatter.formatFileSize
import androidx.compose.foundation.clickable
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
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.BatchResult
import io.github.samolego.canta.ops.UpdateImpact
import io.github.samolego.canta.ops.SafetyAssessment
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.ui.viewmodel.PackageActionRequest
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import io.github.samolego.canta.util.showBiometricPrompt
import kotlinx.coroutines.launch

/** Requests contain immutable package/profile snapshots, including while authorization is pending. */
@Composable
fun PackageActionDialogs(model: AppListViewModel, settings: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf<Pair<PackageAction, BatchResult>?>(null) }
    val request = model.pendingAction
    if (request != null) key(request) {
        var authorized by remember { mutableStateOf(ShizukuPermission.isCantaAuthorized()) }
        if (!authorized) {
            ShizukuRequirementDialog(
                shizukuStatus = ShizukuPermission.checkShizukuActive(context.packageManager),
                onClose = { if (it) authorized = true else model.pendingAction = null },
            )
        } else {
            PackageActionConfirmation(request, model, settings.confirmBeforeUninstall.value,
                onDismiss = { model.pendingAction = null },
                onAgree = { included, reset, approvals, warnings ->
                    model.pendingAction = null
                    val run = {
                        scope.launch {
                            val batch = model.processRequest(context, request, included, reset, approvals, warnings)
                            if (batch.failureCount > 0 || request.action == PackageAction.REMOVE_UPDATES || !settings.hideSuccessDialog.value) {
                                outcome = request.action to batch
                            }
                        }
                        Unit
                    }
                    if (settings.authEnabled.value) showBiometricPrompt(context) { run() } else run()
                },
            )
        }
    }
    outcome?.let { (action, batch) ->
        AlertDialog(onDismissRequest = { outcome = null },
            title = { Text(stringResource(R.string.operation_results)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.operation_batch_result, batch.successCount, batch.failureCount, batch.skippedCount))
                    if (action == PackageAction.REMOVE_UPDATES) Text(stringResource(R.string.update_bytes_freed, formatFileSize(context, batch.freedBytes)))
                    batch.results.filter { !it.success }.forEach { Text(it.message) }
                }
            },
            confirmButton = { TextButton(onClick = { outcome = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = {
                batch.batchId?.let { id ->
                    TextButton(enabled = !model.isOperating, onClick = {
                        scope.launch { outcome = action to model.undoBatch(context, id) }
                    }) { Text(stringResource(R.string.undo_action)) }
                }
            })
    }
}

@Composable
internal fun PackageActionConfirmation(
    request: PackageActionRequest,
    model: AppListViewModel,
    confirmUninstall: Boolean,
    onDismiss: () -> Unit,
    onAgree: (Set<String>, Boolean, Map<String, Set<Int>>, Map<String, Set<String>>) -> Unit,
) {
    val context = LocalContext.current
    var impacts by remember { mutableStateOf<List<UpdateImpact>?>(null) }
    var included by remember { mutableStateOf(request.apps.map { it.packageName }.toSet()) }
    var reset by remember { mutableStateOf(false) }
    var safety by remember { mutableStateOf<Map<String, SafetyAssessment>>(emptyMap()) }
    var warningsAccepted by remember { mutableStateOf(false) }
    val cleanup = request.action == PackageAction.REMOVE_UPDATES
    LaunchedEffect(request) {
        val inspected = if (request.action == PackageAction.REINSTALL) emptyList() else model.inspectUpdates(request)
        safety = if (request.action == PackageAction.REINSTALL) emptyMap() else model.inspectSafety(request)
        impacts = inspected
        if (cleanup) included = inspected.filter { it.updated && it.error == null && it.otherInstalledProfiles.isEmpty() }.map { it.packageName }.toSet()
        else reset = inspected.isNotEmpty() && inspected.all { it.error == null && it.otherInstalledProfiles.isEmpty() }
        included = included.filter { safety[it]?.protected != true && safety[it]?.error == null }.toSet()
        if (request.action == PackageAction.REINSTALL ||
            (!confirmUninstall && request.action == PackageAction.UNINSTALL && inspected.isEmpty() && safety.values.all { it.permits(emptySet()) })) {
            onAgree(included, false, emptyMap(), emptyMap())
        }
    }
    val checked = impacts
    val warnings = if (cleanup) emptyMap() else safety.filter { it.key in included && it.value.warnings.isNotEmpty() }
    val title = request.action.label
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.action_user, request.userId))
                if (checked == null) {
                    Text(stringResource(R.string.checking_update_profiles))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    if (request.action == PackageAction.UNINSTALL) Text(stringResource(R.string.uninstall_confirmation, request.apps.size))
                    if (request.action !in setOf(PackageAction.UNINSTALL, PackageAction.REINSTALL, PackageAction.REMOVE_UPDATES)) {
                        Text(stringResource(R.string.action_app_count, request.apps.size))
                        request.apps.forEach { Text(it.name, style = MaterialTheme.typography.bodySmall) }
                    }
                    checked.filter { it.error != null }.forEach { Text("${it.packageName}: ${it.error}", color = MaterialTheme.colorScheme.error) }
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
                    if (warnings.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().clickable { warningsAccepted = !warningsAccepted }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(warningsAccepted, { warningsAccepted = it })
                            Text(stringResource(R.string.safety_acknowledge))
                        }
                    }
                    if (checked.any { it.updated }) {
                        Text(stringResource(R.string.update_global_warning))
                        Text(stringResource(R.string.update_space_estimate,
                            formatFileSize(context, checked.filter { !cleanup || it.packageName in included }.sumOf { it.reclaimableBytes })))
                        if (!cleanup) {
                            Row(Modifier.fillMaxWidth().clickable { reset = !reset }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = reset, onCheckedChange = { reset = it })
                                Text(stringResource(R.string.reset_to_factory_version))
                            }
                        }
                        checked.filter { it.updated }.forEach { impact ->
                            Column {
                                if (cleanup) {
                                    val selectable = safety[impact.packageName]?.let { !it.protected && it.error == null } ?: false
                                    Row(Modifier.fillMaxWidth().clickable(enabled = selectable) {
                                        included = if (impact.packageName in included) included - impact.packageName else included + impact.packageName
                                    }, verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(impact.packageName in included, enabled = selectable, onCheckedChange = {
                                            included = if (it) included + impact.packageName else included - impact.packageName
                                        })
                                        Text(request.apps.first { it.packageName == impact.packageName }.name)
                                    }
                                }
                                if (impact.otherInstalledProfiles.isNotEmpty()) {
                                    Text(impact.packageName, style = MaterialTheme.typography.labelMedium)
                                    Text(stringResource(if (cleanup) R.string.update_shared_warning else R.string.update_shared_uninstall_warning,
                                        impact.otherInstalledProfiles.joinToString { "${it.name ?: it.kind.name} (${it.id})" }),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else if (cleanup && checked.none { it.error != null }) Text(stringResource(R.string.no_leftover_updates))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = checked != null && checked.none { it.error != null } && included.isNotEmpty() &&
                (warnings.isEmpty() || warningsAccepted), onClick = {
                onAgree(included, reset, checked.orEmpty().associate { impact ->
                    impact.packageName to impact.otherInstalledProfiles.map { it.id }.toSet()
                }, warnings.mapValues { (_, report) -> report.warnings.map { it.key }.toSet() })
            }) { Text(stringResource(title)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
