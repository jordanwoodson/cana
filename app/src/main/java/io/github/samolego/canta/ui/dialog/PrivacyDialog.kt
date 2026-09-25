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
import io.github.samolego.canta.R
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun PrivacyDialog(packageName: String, userId: Int, onDismiss: () -> Unit) {
    val services = CanaServices.getInstance()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<PrivacyState?>(null) }
    var records by remember { mutableStateOf<List<OperationRecord>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PrivacyAction?>(null) }
    var authorize by remember { mutableStateOf(false) }
    LaunchedEffect(packageName, userId, revision) {
        busy = true
        try {
            state = services.privacy.load(packageName, userId)
            records = services.history.records.first()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.message }
        finally { busy = false }
    }
    fun result(result: OperationResult) {
        message = resources.getString(R.string.privacy_results, if (result.success) 1 else 0, if (result.success) 0 else 1) + "\n" + result.message
        revision++
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.privacy)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(packageName, style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.action_user, userId))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it) }
                if (!ShizukuPermission.isCantaAuthorized()) {
                    TextButton(onClick = { authorize = true }) { Text(stringResource(R.string.privacy_connect)) }
                }
                state?.let { current ->
                    if (current.sharedPackages.size > 1) Text(stringResource(R.string.privacy_shared_uid, current.sharedPackages.joinToString()))
                    for (action in PrivacyAction.entries) {
                        HorizontalDivider()
                        Text(stringResource(action.title), style = MaterialTheme.typography.titleSmall)
                        current.errors[action]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        val value = current.values[action]
                        if (action == PrivacyAction.NETWORK) {
                            Text(stringResource(R.string.privacy_network_description), style = MaterialTheme.typography.bodySmall)
                            if (value == null && current.desiredBlock) Text(stringResource(R.string.privacy_mismatch))
                        }
                        if (action == PrivacyAction.METERED) {
                            Text(stringResource(R.string.privacy_metered_description), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.privacy_desired_metered, current.desiredMetered.toString()))
                            if (current.desiredMetered && (value == null || value.getInt("meteredPolicy") and 1 == 0))
                                Text(stringResource(R.string.privacy_mismatch), color = MaterialTheme.colorScheme.error)
                        }
                        if (value != null) {
                            when (action) {
                                PrivacyAction.PERMISSIONS -> {
                                    val permissions = value.getJSONArray("permissions")
                                    if (permissions.length() == 0) Text(stringResource(R.string.privacy_no_permissions))
                                    for (index in 0 until permissions.length()) {
                                        val permission = permissions.getJSONObject(index)
                                        Text(stringResource(R.string.privacy_permission_state, permission.getString("name").removePrefix("android.permission."),
                                            permission.getBoolean("granted").toString(), permission.getInt("flags")), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                PrivacyAction.BACKGROUND -> {
                                    Text(stringResource(R.string.privacy_background_state, value.getString("runAnyInBackground"), value.getInt("standbyBucket")))
                                    if (value.getInt("standbyBucket") == 5) Text(stringResource(R.string.privacy_exempt), style = MaterialTheme.typography.bodySmall)
                                }
                                PrivacyAction.METERED -> Text(stringResource(R.string.privacy_metered_state,
                                    (value.getInt("meteredPolicy") and 1 != 0).toString(), (value.getInt("meteredPolicy") and 4 != 0).toString()))
                                PrivacyAction.NETWORK -> {
                                    val actual = value.getBoolean("chainEnabled") && value.getInt("networkRule") == 2
                                    Text(stringResource(R.string.privacy_network_state, current.desiredBlock.toString(), actual.toString()))
                                    if (current.desiredBlock != actual) Text(stringResource(R.string.privacy_mismatch), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Row {
                            TextButton(enabled = !busy && value != null && packageName !in services.safety.alwaysProtected,
                                onClick = { pending = action }) {
                                Text(stringResource(when (action) {
                                    PrivacyAction.PERMISSIONS -> R.string.privacy_revoke
                                    PrivacyAction.BACKGROUND, PrivacyAction.METERED -> R.string.privacy_restrict
                                    PrivacyAction.NETWORK -> R.string.privacy_block
                                }))
                            }
                            val previous = records.lastOrNull { candidate -> candidate.packageName == packageName && candidate.userId == userId &&
                                candidate.action == action.key && candidate.undoOf.isEmpty() && (candidate.changed || !candidate.completed) &&
                                records.none { it.undoOf == candidate.id && it.completed && it.success } }
                            if (previous != null) TextButton(enabled = !busy && value != null, onClick = { scope.launch {
                                busy = true
                                try { result(services.privacy.undo(previous)) } finally { busy = false }
                            } }) { Text(stringResource(R.string.privacy_revert)) }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.close)) } })
    if (authorize) ShizukuRequirementDialog({ authorize = false; revision++ }, ShizukuPermission.checkShizukuActive(context.packageManager))
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
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(stringResource(R.string.privacy_confirm)) },
            text = { Column {
                Text(stringResource(action.title))
                Text(stringResource(R.string.action_user, userId))
                if (action == PrivacyAction.NETWORK) Text(stringResource(R.string.privacy_network_description))
                if (action == PrivacyAction.METERED) Text(stringResource(R.string.privacy_metered_description))
                error?.let { Text(it) }
                if (reports == null && error == null) LinearProgressIndicator()
                reports?.forEach { (name, report) ->
                    if (report.protected) Text(stringResource(R.string.safety_protected, name))
                    report.error?.let { Text(it) }
                    report.warnings.forEach { Text("${it.kind}: ${it.detail}") }
                }
                if ((reports?.size ?: 0) > 1) Text(stringResource(R.string.privacy_shared_uid, reports!!.keys.joinToString()))
                if (warning) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(accepted, { accepted = it }); Text(stringResource(R.string.safety_acknowledge))
                }
            } }, confirmButton = {
                TextButton(enabled = reports != null && error == null && reports!!.values.all { !it.protected && it.error == null } && (!warning || accepted),
                    onClick = {
                        val approved = reports!!.values.flatMap { it.warnings }.map { it.key }.toSet() + if (accepted) setOf("shared-uid") else emptySet()
                        pending = null
                        scope.launch { busy = true; try { result(services.privacy.restrict(packageName, userId, action, approved)) } finally { busy = false } }
                    }) { Text(stringResource(R.string.ok)) }
            }, dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
