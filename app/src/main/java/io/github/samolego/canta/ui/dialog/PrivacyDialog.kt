package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import io.github.samolego.canta.R
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import io.github.samolego.canta.util.withPackageAuthentication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun PrivacyDialog(packageName: String, userId: Int, onDismiss: () -> Unit) =
    PrivacyDialog(packageName, userId, embedded = false, onDismiss = onDismiss)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacyDialog(packageName: String, userId: Int, embedded: Boolean, onDismiss: () -> Unit) {
    val services = CanaServices.getInstance()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var state by remember(packageName, userId) { mutableStateOf<PrivacyState?>(null) }
    var records by remember(packageName, userId) { mutableStateOf<List<OperationRecord>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PrivacyAction?>(null) }
    var pendingUndo by remember { mutableStateOf<OperationRecord?>(null) }
    var forget by remember { mutableStateOf<PrivacyAction?>(null) }
    var authorize by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(false) }
    var selected by remember(packageName, userId) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(packageName, userId, revision) {
        busy = true
        try {
            state = services.privacy.load(packageName, userId)
            records = services.history.records.first()
            val entries = state?.values?.get(PrivacyAction.PERMISSIONS)?.getJSONArray("permissions")
            selected = if (entries == null) emptySet() else (0 until entries.length()).map { entries.getJSONObject(it) }
                .filter { PrivacyPolicy.mutablePermission(it.getInt("flags")) }.map { it.getString("name") }.toSet()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = resources.getString(R.string.privacy_store_unavailable) }
        finally { busy = false }
    }
    fun perform(action: String, execute: suspend (String) -> OperationResult) {
        scope.launch {
            withPackageAuthentication(context) {
                busy = true
                try {
                    val result = services.batches.run(resources.getString(R.string.privacy),
                        listOf(BatchItem("privacy", packageName, userId, action))) { _, batch -> execute(batch) }
                    message = result.results.joinToString("\n") { it.message }
                    revision++
                } finally { busy = false }
            }
        }
    }
    val content: @Composable () -> Unit = {
        Column((if (embedded) Modifier.fillMaxWidth() else Modifier.heightIn(max = 560.dp))
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(packageName, style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.action_user, userId))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it) }
            val connected = state?.connected ?: ShizukuPermission.isCantaAuthorized()
            if (!connected) PrivacyDisconnectedNotice(onConnect = { authorize = true }, onRetry = { revision++ })
            else TextButton(enabled = !busy, onClick = { revision++ }) { Text(stringResource(R.string.privacy_retry)) }
            state?.let { current ->
                if (current.sharedPackages.size > 1) Text(stringResource(R.string.privacy_shared_uid, current.sharedPackages.joinToString()))
                for (action in PrivacyAction.entries) {
                    HorizontalDivider()
                    Text(stringResource(action.title), style = MaterialTheme.typography.titleSmall)
                    val value = current.values[action]
                    if (action == PrivacyAction.NETWORK) Text(stringResource(R.string.privacy_network_description), style = MaterialTheme.typography.bodySmall)
                    if (action == PrivacyAction.METERED) Text(stringResource(R.string.privacy_metered_description), style = MaterialTheme.typography.bodySmall)
                    current.saved[action]?.let { saved ->
                        Text(stringResource(R.string.privacy_saved_restriction))
                        Text(stringResource(privacyStatusLabel(if (!connected && saved.status != "needs_review") "disconnected" else saved.status)))
                    }
                    if (action in setOf(PrivacyAction.NETWORK, PrivacyAction.METERED) && action !in current.saved)
                        Text(stringResource(R.string.privacy_no_saved_restriction))
                    if (value == null) Text(stringResource(R.string.privacy_live_unavailable))
                    else when (action) {
                        PrivacyAction.PERMISSIONS -> {
                            val entries = value.getJSONArray("permissions")
                            if (entries.length() == 0) Text(stringResource(R.string.privacy_no_permissions))
                            else Text(stringResource(R.string.privacy_select_permissions))
                            for (index in 0 until entries.length()) {
                                val permission = entries.getJSONObject(index)
                                val name = permission.getString("name")
                                val mutable = PrivacyPolicy.mutablePermission(permission.getInt("flags"))
                                val label = remember(name) { runCatching {
                                    context.packageManager.getPermissionInfo(name, 0).loadLabel(context.packageManager).toString()
                                }.getOrDefault(name.removePrefix("android.permission.")) }
                                Row(Modifier.testTag("privacy-permission-$name"), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = name in selected, enabled = !busy && mutable,
                                        onCheckedChange = { selected = if (it) selected + name else selected - name })
                                    Column {
                                        Text(label)
                                        Text(stringResource(if (!mutable) R.string.privacy_permission_fixed else if (permission.getBoolean("granted"))
                                            R.string.privacy_permission_granted else R.string.privacy_permission_denied), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        PrivacyAction.BACKGROUND -> {
                            Text(stringResource(when (value.getString("runAnyInBackground")) {
                                "allow" -> R.string.privacy_live_unrestricted
                                "ignore", "deny" -> R.string.privacy_live_restricted
                                "foreground" -> R.string.privacy_live_foreground_only
                                else -> R.string.privacy_live_system_default
                            }))
                            if (value.getInt("standbyBucket") == 5) Text(stringResource(R.string.privacy_exempt), style = MaterialTheme.typography.bodySmall)
                        }
                        PrivacyAction.METERED -> Text(stringResource(if (value.getInt("meteredPolicy") and 1 != 0) R.string.privacy_live_restricted else R.string.privacy_live_unrestricted))
                        PrivacyAction.NETWORK -> Text(stringResource(if (value.getBoolean("chainEnabled") && value.getInt("networkRule") == 2)
                            R.string.privacy_live_blocked else R.string.privacy_live_allowed))
                    }
                    FlowRow {
                        TextButton(enabled = !busy && value != null && packageName !in services.safety.alwaysProtected &&
                            (action != PrivacyAction.PERMISSIONS || selected.isNotEmpty()), onClick = { pendingUndo = null; pending = action }) {
                            Text(stringResource(when (action) {
                                PrivacyAction.PERMISSIONS -> R.string.privacy_revoke_selected
                                PrivacyAction.BACKGROUND, PrivacyAction.METERED -> R.string.privacy_restrict
                                PrivacyAction.NETWORK -> R.string.privacy_block
                            }))
                        }
                        val previous = records.lastOrNull { candidate -> candidate.packageName == packageName && candidate.userId == userId &&
                            candidate.action == action.key && candidate.undoOf.isEmpty() && (candidate.changed || !candidate.completed) &&
                            records.none { it.undoOf == candidate.id && it.completed && it.success } }
                        if (previous != null) TextButton(enabled = !busy && value != null, onClick = {
                            val needsConsent = runCatching { PrivacyReviewPolicy.undoNeedsConsent(action.key,
                                SnapshotState.read(previous.previousState), checkNotNull(value)) }.getOrDefault(true)
                            if (needsConsent) { pendingUndo = previous; pending = action }
                            else perform(action.key) { batch -> services.privacy.undo(previous, batch) }
                        }) { Text(stringResource(R.string.privacy_revert)) }
                        if (action in current.saved) TextButton(enabled = !busy, onClick = { forget = action }) {
                            Text(stringResource(R.string.privacy_forget))
                        }
                    }
                }
                if (current.errors.isNotEmpty() || current.saved.values.any { it.statusMessage.isNotEmpty() }) {
                    TextButton(onClick = { diagnostics = !diagnostics }) {
                        Text(stringResource(if (diagnostics) R.string.privacy_hide_diagnostics else R.string.privacy_diagnostics))
                    }
                    if (diagnostics) {
                        current.errors.forEach { (action, error) -> Text("${resources.getString(action.title)}: $error", style = MaterialTheme.typography.bodySmall) }
                        current.saved.values.filter { it.statusMessage.isNotEmpty() }.forEach { Text(it.statusMessage, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
    if (embedded) content() else AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.privacy)) },
        text = content, confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.close)) } })
    if (authorize) ShizukuRequirementDialog({ authorize = false; revision++ }, ShizukuPermission.checkShizukuActive(context.packageManager))
    forget?.let { action ->
        AlertDialog(onDismissRequest = { forget = null }, title = { Text(stringResource(R.string.privacy_forget)) },
            text = { Text(stringResource(R.string.privacy_forget_explanation)) }, confirmButton = {
                TextButton(onClick = { forget = null; perform("${action.key}_forget") { services.privacy.forgetDesired(packageName, userId, action) } }) {
                    Text(stringResource(R.string.privacy_forget))
                }
            }, dismissButton = { TextButton(onClick = { forget = null }) { Text(stringResource(R.string.cancel)) } })
    }
    pending?.let { action ->
        var reports by remember(action) { mutableStateOf<Map<String, SafetyAssessment>?>(null) }
        var error by remember(action) { mutableStateOf<String?>(null) }
        var accepted by remember(action) { mutableStateOf(false) }
        LaunchedEffect(action) {
            try { reports = services.privacy.assess(packageName, userId) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
        }
        val warning = reports?.values?.any { it.warnings.isNotEmpty() } == true || (reports?.size ?: 0) > 1
        AlertDialog(onDismissRequest = { pending = null; pendingUndo = null }, title = {
            Text(stringResource(if (pendingUndo != null) R.string.privacy_revert else R.string.privacy_confirm))
        }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(packageName)
                Text(stringResource(R.string.action_user, userId))
                Text(stringResource(action.title))
                if (action == PrivacyAction.PERMISSIONS && pendingUndo == null) Text(stringResource(R.string.privacy_selected_permission_count, selected.size))
                error?.let { Text(it) }
                if (reports == null && error == null) LinearProgressIndicator()
                reports?.forEach { (name, report) ->
                    if (report.protected) Text(stringResource(R.string.safety_protected, name))
                    report.error?.let { Text(it) }
                    report.warnings.forEach { Text("$name: ${it.kind}: ${it.detail}") }
                }
                if ((reports?.size ?: 0) > 1) Text(stringResource(R.string.privacy_shared_uid, reports!!.keys.joinToString()))
                if (warning) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(accepted, { accepted = it }); Text(stringResource(R.string.safety_acknowledge))
                }
            }
        }, confirmButton = {
            TextButton(enabled = reports != null && error == null && reports!!.values.all { !it.protected && it.error == null } && (!warning || accepted), onClick = {
                val approved = PrivacyOps.approvalKeys(packageName, userId, reports!!)
                val permissions = if (action == PrivacyAction.PERMISSIONS) selected.toSet() else null
                val undoRecord = pendingUndo
                pending = null
                pendingUndo = null
                perform(action.key) { batch -> if (undoRecord != null) services.privacy.undo(undoRecord, batch, approved)
                    else services.privacy.restrict(packageName, userId, action, approved, batch, selectedPermissions = permissions) }
            }) { Text(stringResource(R.string.ok)) }
        }, dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
fun PrivacyDisconnectedNotice(onConnect: () -> Unit, onRetry: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.privacy_disconnected_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.privacy_disconnected_body))
            Row {
                TextButton(onClick = onConnect) { Text(stringResource(R.string.privacy_connect)) }
                TextButton(onClick = onRetry) { Text(stringResource(R.string.privacy_retry)) }
            }
        }
    }
}

fun privacyStatusLabel(status: String): Int = when (status) {
    "verified" -> R.string.privacy_status_verified
    "failed" -> R.string.privacy_status_failed
    "disconnected" -> R.string.privacy_status_disconnected
    "pending" -> R.string.privacy_status_pending
    else -> R.string.privacy_status_review
}
