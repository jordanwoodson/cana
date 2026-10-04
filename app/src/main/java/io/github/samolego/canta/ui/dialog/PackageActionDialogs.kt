package io.github.samolego.canta.ui.dialog

import io.github.samolego.canta.ui.menu.profileName

import android.text.format.Formatter.formatFileSize
import android.text.format.Formatter.formatShortFileSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.R
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.ops.BatchFeedbackPolicy
import io.github.samolego.canta.ops.BatchFeedbackPresentation
import io.github.samolego.canta.ops.BatchResult
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ops.UpdateImpact
import io.github.samolego.canta.ops.SafetyAssessment
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.ui.viewmodel.PackageActionRequest
import io.github.samolego.canta.ui.component.CanaBanner
import io.github.samolego.canta.ui.component.RiskChip
import io.github.samolego.canta.ui.component.StatusTone
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import io.github.samolego.canta.util.withPackageAuthentication
import io.github.samolego.canta.util.apps.knownApkTotal
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Requests contain immutable package/profile snapshots, including while authorization is pending. */
private data class PackageOutcome(val action: PackageAction, val batch: BatchResult,
    val records: List<OperationRecord>, val presentation: BatchFeedbackPresentation)

@Composable
fun PackageActionDialogs(model: AppListViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val services = CanaServices.getInstance()
    val noChangesMessage = stringResource(R.string.feedback_no_changes)
    val undoLabel = stringResource(R.string.undo_action)
    var outcome by remember { mutableStateOf<PackageOutcome?>(null) }
    var undoRecords by remember { mutableStateOf<List<OperationRecord>?>(null) }
    val request = model.pendingAction
    if (request != null) key(request) {
        var authorized by remember { mutableStateOf(ShizukuPermission.isCantaAuthorized()) }
        if (!authorized) {
            ShizukuRequirementDialog(
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
                            val records = batch.batchId?.let { id -> services.history.records.first()
                                .filter { it.batchId == id && it.changed }.asReversed() }.orEmpty()
                            val decision = BatchFeedbackPolicy.decide(batch, records.isNotEmpty())
                            if (decision.presentation == BatchFeedbackPresentation.SUCCESS_SNACKBAR) {
                                val count = batch.successCount
                                val text = when {
                                    count == 0 -> noChangesMessage
                                    request.action == PackageAction.REMOVE_UPDATES -> context.resources.getQuantityString(
                                        R.plurals.feedback_removed_updates, count, count, formatShortFileSize(context, batch.freedBytes))
                                    else -> context.resources.getQuantityString(request.action.successPlural(), count, count)
                                }
                                services.uiMessages.post(text,
                                    actionLabel = if (decision.canUndo) undoLabel else null,
                                    onAction = if (decision.canUndo) ({ undoRecords = records }) else null,
                                    withDismiss = decision.canUndo)
                            } else {
                                outcome = PackageOutcome(request.action, batch, records, decision.presentation)
                            }
                        } }
                        Unit
                    }
                    run()
                },
            )
        }
    }
    outcome?.let { result ->
        val action = result.action
        val batch = result.batch
        AlertDialog(onDismissRequest = { outcome = null },
            title = { Text(stringResource(if (result.presentation == BatchFeedbackPresentation.FAILURE_DIALOG)
                R.string.feedback_failed_title else if (result.presentation == BatchFeedbackPresentation.VERIFICATION_DIALOG)
                R.string.feedback_verification_title else R.string.operation_results)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.batch_outcome_counts, batch.successCount, batch.failureCount, batch.skippedCount, batch.unknownCount))
                    if (action == PackageAction.REMOVE_UPDATES) Text(stringResource(R.string.update_bytes_freed, formatFileSize(context, batch.freedBytes)))
                    batch.results.sortedBy { item -> when {
                        !item.success && !item.outcomeUnknown -> 0
                        item.outcomeUnknown -> 1
                        else -> 2
                    } }.forEach { Text(it.message,
                        color = if (!it.success && !it.outcomeUnknown) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface) }
                }
            },
            confirmButton = { TextButton(onClick = { outcome = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = {
                if (result.records.isNotEmpty()) {
                    TextButton(enabled = !model.isOperating, onClick = {
                        undoRecords = result.records
                        outcome = null
                    }) { Text(stringResource(R.string.undo_action)) }
                }
            })
    }
    undoRecords?.let { records -> io.github.samolego.canta.ui.screen.UndoBatchDialog(records,
        { undoRecords = null }) { result ->
            val decision = BatchFeedbackPolicy.decide(result, false)
            if (decision.presentation == BatchFeedbackPresentation.SUCCESS_SNACKBAR) {
                services.uiMessages.post(context.resources.getQuantityString(R.plurals.feedback_undid,
                    result.successCount, result.successCount))
            } else outcome = PackageOutcome(PackageAction.REINSTALL, result, emptyList(), decision.presentation)
            undoRecords = null
        } }
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
    val reset = false
    var safety by remember { mutableStateOf<Map<String, SafetyAssessment>>(emptyMap()) }
    var warningsAccepted by remember(included, reset) { mutableStateOf(false) }
    var inspectionError by remember { mutableStateOf<String?>(null) }
    val cleanup = request.action == PackageAction.REMOVE_UPDATES
    LaunchedEffect(request) {
        try {
        val inspected = if (request.action == PackageAction.REINSTALL) emptyList() else model.inspectUpdates(request)
        safety = if (request.action == PackageAction.REINSTALL) emptyMap() else model.inspectSafety(request)
        impacts = inspected
        if (cleanup) included = inspected.filter { it.updated && !it.installed && it.error == null && it.otherInstalledProfiles.isEmpty() }.map { it.packageName }.toSet()

        included = included.filter { pkg -> safety[pkg]?.protected != true && safety[pkg]?.error == null && inspected.none { it.packageName == pkg && it.error != null } }.toSet()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { inspectionError = e.message ?: unavailableMessage }
    }
    val checked = impacts
    val warnings = if (cleanup) emptyMap() else safety.filter { it.key in included && it.value.warnings.isNotEmpty() }
    val includedApps = request.apps.filter { it.packageName in included }
    val riskyRemoval = request.action in setOf(PackageAction.UNINSTALL, PackageAction.UNINSTALL_KEEP_DATA) &&
        includedApps.any { it.removalInfo in setOf(RemovalRecommendation.EXPERT, RemovalRecommendation.UNSAFE) }
    val skipped = request.requestedPackages.size - included.size
    val profile = profileName(request.userId)
    val title = pluralStringResource(request.action.confirmationTitle(), included.size, included.size, profile)
    val updateConsent = (cleanup || reset) && checked.orEmpty().any { it.packageName in included && it.otherInstalledProfiles.isNotEmpty() }
    val needsConsent = warnings.isNotEmpty() || updateConsent
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (skipped > 0) Text(pluralStringResource(R.plurals.confirm_skipped_count, skipped, skipped),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(when (request.action) {
                    PackageAction.UNINSTALL -> if (includedApps.any { !it.isSystemApp }) R.string.confirm_uninstall_user_consequence
                        else R.string.confirm_uninstall_system_consequence
                    PackageAction.UNINSTALL_KEEP_DATA -> R.string.plan_keep_data
                    PackageAction.REINSTALL -> R.string.plan_restore
                    PackageAction.REMOVE_UPDATES -> R.string.plan_update_loss
                    PackageAction.DISABLE -> R.string.confirm_disable_consequence
                    PackageAction.SUSPEND -> R.string.confirm_suspend_consequence
                    else -> R.string.plan_reversible
                }))
                if (request.action in setOf(PackageAction.UNINSTALL, PackageAction.UNINSTALL_KEEP_DATA))
                    Text(stringResource(R.string.uninstall_profile_only))
                if (cleanup) Text(stringResource(R.string.update_cleanup_in_use))
                inspectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (checked == null && inspectionError == null) {
                    Text(stringResource(R.string.checking_update_profiles))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    request.requestedPackages.forEach { pkg ->
                        val app = request.apps.find { it.packageName == pkg }
                        val assessment = safety[pkg]
                        val impact = checked.orEmpty().find { it.packageName == pkg }
                        val eligible = app != null && assessment?.protected != true && assessment?.error == null && impact?.error == null &&
                            (!cleanup || impact?.let { it.updated && !it.installed && it.otherInstalledProfiles.isEmpty() } == true)
                        Row(Modifier.fillMaxWidth().toggleable(pkg in included, enabled = eligible, role = Role.Checkbox,
                            onValueChange = { included = if (it) included + pkg else included - pkg }), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(pkg in included, enabled = eligible, onCheckedChange = null)
                            Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(app?.name ?: pkg, style = MaterialTheme.typography.bodyMedium)
                                if (app != null) Text(pkg, style = MaterialTheme.typography.bodySmall)
                                app?.removalInfo?.let { RiskChip(it) }
                                val reason = when {
                                    app == null -> stringResource(R.string.plan_unavailable)
                                    assessment?.protected == true -> stringResource(R.string.safety_protected, pkg)
                                    assessment?.error != null -> assessment.error
                                    impact?.error != null -> impact.error
                                    cleanup && impact?.installed == true -> stringResource(R.string.update_cleanup_in_use)
                                    cleanup && impact?.otherInstalledProfiles?.isNotEmpty() == true -> stringResource(R.string.update_cleanup_in_use)
                                    cleanup && impact?.updated != true -> stringResource(R.string.no_leftover_updates)
                                    pkg !in included -> stringResource(R.string.plan_excluded)
                                    else -> null
                                }
                                reason?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                    if (included.isEmpty()) Text(stringResource(R.string.plan_empty))
                    checked.orEmpty().filter { it.error != null }.forEach { Text("${it.packageName}: ${it.error}", color = MaterialTheme.colorScheme.error) }
                    safety.filterValues { it.protected || it.error != null }.forEach { (name, assessment) ->
                        Text(assessment.error ?: stringResource(R.string.safety_protected, name), color = MaterialTheme.colorScheme.error)
                    }
                    if (warnings.isNotEmpty()) CanaBanner(Icons.Default.Warning,
                        stringResource(R.string.confirm_warning_title), StatusTone.ERROR,
                        details = {
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
                        })
                    if (cleanup && checked.orEmpty().any { it.updated }) {
                        CanaBanner(Icons.Default.Warning, stringResource(R.string.update_global_warning), StatusTone.ERROR,
                            details = {
                                val selectedUpdateBytes = knownApkTotal(checked.orEmpty()
                                    .filter { it.updated && it.packageName in included }.map { it.sizeBytes })
                                Text(stringResource(R.string.selected_update_apks, selectedUpdateBytes?.let {
                                    formatFileSize(context, it)
                                } ?: stringResource(R.string.app_size_unavailable)))
                                checked.orEmpty().filter { it.updated }.forEach { impact ->
                                    if (impact.otherInstalledProfiles.isNotEmpty()) {
                                        Text(impact.packageName, style = MaterialTheme.typography.labelMedium)
                                        Text(stringResource(R.string.update_cleanup_profile_names,
                                            impact.otherInstalledProfiles.joinToString { "${it.name ?: it.kind.name} (${it.id})" }),
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            })
                    } else if (cleanup && checked.orEmpty().none { it.error != null }) Text(stringResource(R.string.no_leftover_updates))
                    if (needsConsent) {
                        io.github.samolego.canta.ui.component.CheckboxRow(warningsAccepted, { warningsAccepted = it }) {
                            Text(stringResource(R.string.safety_acknowledge))
                        }
                    }
                    if (riskyRemoval) TextButton(onClick = {
                        onDismiss()
                        model.requestAction(PackageAction.DISABLE, included.toList(), request.userId)
                    }) { Text(stringResource(R.string.confirm_disable_instead)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = checked != null && checked.none { it.packageName in included && it.error != null } && included.isNotEmpty() &&
                (!needsConsent || warningsAccepted), onClick = {
                onAgree(included, reset, checked.orEmpty().associate { impact ->
                    impact.packageName to impact.otherInstalledProfiles.map { it.id }.toSet()
                }, warnings.mapValues { (_, report) -> report.warnings.map { it.key }.toSet() })
            }, colors = ButtonDefaults.textButtonColors(contentColor = if (request.action in setOf(PackageAction.UNINSTALL,
                    PackageAction.UNINSTALL_KEEP_DATA, PackageAction.REMOVE_UPDATES)) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary)) { Text(stringResource(request.action.label)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun PackageAction.confirmationTitle(): Int = when (this) {
    PackageAction.UNINSTALL -> R.plurals.confirm_uninstall_title
    PackageAction.UNINSTALL_KEEP_DATA -> R.plurals.confirm_uninstall_keep_data_title
    PackageAction.DISABLE -> R.plurals.confirm_disable_title
    PackageAction.ENABLE -> R.plurals.confirm_enable_title
    PackageAction.SUSPEND -> R.plurals.confirm_suspend_title
    PackageAction.UNSUSPEND -> R.plurals.confirm_unsuspend_title
    PackageAction.REINSTALL -> R.plurals.confirm_reinstall_title
    PackageAction.REMOVE_UPDATES -> R.plurals.confirm_remove_updates_title
}

private fun PackageAction.successPlural(): Int = when (this) {
    PackageAction.DISABLE -> R.plurals.feedback_disabled
    PackageAction.ENABLE -> R.plurals.feedback_enabled
    PackageAction.SUSPEND -> R.plurals.feedback_suspended
    PackageAction.UNSUSPEND -> R.plurals.feedback_unsuspended
    PackageAction.UNINSTALL -> R.plurals.feedback_uninstalled
    PackageAction.UNINSTALL_KEEP_DATA -> R.plurals.feedback_uninstalled_keep_data
    PackageAction.REINSTALL -> R.plurals.feedback_reinstalled
    PackageAction.REMOVE_UPDATES -> R.plurals.feedback_removed_updates
}
