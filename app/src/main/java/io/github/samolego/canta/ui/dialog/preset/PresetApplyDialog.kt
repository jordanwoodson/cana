package io.github.samolego.canta.ui.dialog.preset

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
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PresetApplyDialog(preset: CantaPresetData, userId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val services = CanaServices.getInstance()
    val scope = rememberCoroutineScope()
    var authorized by remember { mutableStateOf(ShizukuPermission.isCantaAuthorized()) }
    if (!authorized) {
        io.github.samolego.canta.ui.dialog.ShizukuRequirementDialog({ if (it) authorized = true else onDismiss() },
            ShizukuPermission.checkShizukuActive(context.packageManager))
        return
    }
    var warnings by remember { mutableStateOf<Map<String, SafetyAssessment>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var accepted by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BatchResult?>(null) }
    LaunchedEffect(preset, userId) {
        try {
            val reports = services.safety.inspect(preset.apps.toList(), userId, removing = true).toMutableMap()
            for (entry in preset.lockdown) {
                val privacy = services.privacy.assess(entry.packageName, userId)
                if (privacy.size > 1) shared = true
                reports.putAll(privacy)
            }
            warnings = reports
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
    }
    val needsConsent = warnings?.values?.any { it.warnings.isNotEmpty() } == true || shared
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.apply_preset)) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(preset.name)
            Text(stringResource(R.string.action_user, userId))
            for (pkg in preset.apps) Text(stringResource(R.string.preset_remove_entry, pkg))
            for (entry in preset.lockdown) {
                Text(entry.packageName)
                entry.actions().forEach { Text(stringResource(it.title), style = MaterialTheme.typography.bodySmall) }
            }
            if (preset.lockdown.any { it.blockNetwork }) Text(stringResource(R.string.privacy_network_description))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy || warnings == null && error == null) LinearProgressIndicator()
            warnings?.forEach { (name, assessment) ->
                if (assessment.protected) Text(stringResource(R.string.safety_protected, name))
                assessment.error?.let { Text(it) }
                assessment.warnings.forEach { Text("$name: ${it.kind} · ${it.detail}") }
            }
            if (shared) Text(stringResource(R.string.privacy_shared_uid, warnings!!.keys.joinToString()))
            if (needsConsent && result == null) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(accepted, { accepted = it }); Text(stringResource(R.string.safety_acknowledge))
            }
            result?.let { batch ->
                Text(stringResource(R.string.privacy_results, batch.successCount, batch.failureCount))
                batch.results.forEach { Text(it.message, style = MaterialTheme.typography.bodySmall) }
            }
        } }, confirmButton = {
            if (result != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            else TextButton(enabled = !busy && warnings != null && error == null && (!needsConsent || accepted), onClick = {
                busy = true
                val approved = warnings!!.values.flatMap { it.warnings }.map { it.key }.toSet() + if (accepted) setOf("shared-uid") else emptySet()
                scope.launch {
                    try { result = services.presets.apply(preset, listOf(userId), mapOf(userId to approved)) }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.apply_preset)) }
        }, dismissButton = { if (result == null) TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
