package io.github.samolego.canta.ui.component

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.github.samolego.canta.data.proto.ManagementState
import io.github.samolego.canta.data.proto.OtaPackageChange
import io.github.samolego.canta.ops.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun OtaBanner() {
    val services = CanaServices.getInstance()
    val state by services.management.state.collectAsStateWithLifecycle(ManagementState.getDefaultInstance())
    val scope = rememberCoroutineScope()
    var review by remember { mutableStateOf<List<OtaPackageChange>?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) scope.launch { services.ota.check() }
    }
    if (state.pendingCount > 0) Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(stringResource(R.string.ota_summary, state.pendingCount))
            Row {
                TextButton(onClick = { review = state.pendingList.toList() }) { Text(stringResource(R.string.ota_review)) }
                if (Build.VERSION.SDK_INT >= 33) TextButton(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text(stringResource(R.string.ota_enable_notifications))
                }
            }
        }
    }
    review?.let { OtaReviewDialog(it) { review = null } }
}

@Composable
private fun OtaReviewDialog(changes: List<OtaPackageChange>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val services = CanaServices.getInstance()
    val scope = rememberCoroutineScope()
    val batchTitle = stringResource(R.string.ota_reapply)
    var selected by remember { mutableStateOf(changes.filter { it.returned }.toSet()) }
    var reports by remember { mutableStateOf<Map<Int, Map<String, SafetyAssessment>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var accepted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BatchResult?>(null) }
    LaunchedEffect(selected) {
        reports = null; error = null; accepted = false
        try { reports = selected.groupBy { it.userId }.mapValues { (user, entries) ->
            services.safety.inspect(entries.map { it.packageName }, user, removing = true)
        } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: e.toString() }
    }
    val warnings = reports?.values.orEmpty().flatMap { it.values }.flatMap { it.warnings }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.ota_title)) },
        text = { Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ota_review_description))
            for (change in changes) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(change in selected, enabled = !busy && result == null, onCheckedChange = {
                    selected = if (it) selected + change else selected - change
                })
                Column {
                    Text(change.packageName)
                    Text(stringResource(if (change.returned) R.string.ota_returned else R.string.ota_new, change.userId), style = MaterialTheme.typography.bodySmall)
                    reports?.get(change.userId)?.get(change.packageName)?.let { report ->
                        if (report.protected) Text(stringResource(R.string.safety_protected, change.packageName))
                        report.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            warnings.forEach { Text("${it.kind}: ${it.detail}") }
            if (warnings.isNotEmpty() && result == null) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(accepted, { accepted = it }); Text(stringResource(R.string.safety_acknowledge))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy || reports == null && error == null) LinearProgressIndicator()
            result?.let { batch ->
                Text(stringResource(R.string.batch_outcome_counts, batch.successCount, batch.failureCount, batch.skippedCount, batch.unknownCount))
                batch.results.forEach { Text(it.message, style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(enabled = !busy, onClick = { scope.launch { services.management.dismiss(changes); services.ota.check(); onDismiss() } }) {
                Text(stringResource(R.string.ota_dismiss))
            }
        } }, confirmButton = {
            if (result != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            else TextButton(enabled = !busy && selected.isNotEmpty() && reports != null && (warnings.isEmpty() || accepted), onClick = {
                busy = true
                val captured = selected.toList()
                val approved = reports!!.mapValues { (_, values) -> values.values.flatMap { it.warnings }.map { it.key }.toSet() }
                scope.launch {
                    try { withPackageAuthentication(context) {
                        result = services.batches.run(batchTitle, captured.map {
                            BatchItem("${it.userId}:${it.packageName}", it.packageName, it.userId, "uninstall")
                        }) { change, batch ->
                            val operation = services.packageOps.uninstall(change.packageName, change.userId, batchId = batch, approvedWarnings = approved[change.userId].orEmpty())
                            try { services.ota.check() }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { io.github.samolego.canta.util.LogUtils.e("OTA", "Post-operation check failed", e) }
                            operation.copy(message = "${change.packageName} · user ${change.userId}: ${operation.message}")
                        }
                    } } finally { busy = false }
                }
            }) { Text(stringResource(R.string.ota_reapply)) }
        }, dismissButton = { if (result == null) TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
