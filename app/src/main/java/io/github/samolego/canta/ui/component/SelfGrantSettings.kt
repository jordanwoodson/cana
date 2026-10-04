package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ui.dialog.ShizukuRequirementDialog
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SelfGrantSettings() {
    val grants = remember { CanaServices.getInstance().selfGrants }
    val status by grants.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { grants.refreshStatus() }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableIntStateOf(0) }
    var connect by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun grant() {
        scope.launch {
            busy = true
            result = null
            failed = 0
            try {
                val results = grants.grantMissing()
                failed = results.count { !it.success }
                result = results.filterNot { it.success }.joinToString("\n") { it.message }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed = 1; result = e.message }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        ListItem(headlineContent = { Text(stringResource(R.string.settings_permission_secure)) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            supportingContent = { StatusChip(stringResource(if (status.secureSettings) R.string.grant_allowed else R.string.settings_permission_missing),
                if (status.secureSettings) Icons.Default.CheckCircle else Icons.Default.Warning,
                if (status.secureSettings) StatusTone.NEUTRAL else StatusTone.WARNING) })
        ListItem(headlineContent = { Text(stringResource(R.string.settings_permission_usage)) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            supportingContent = { StatusChip(stringResource(if (status.usageStats) R.string.grant_allowed else R.string.settings_permission_missing),
                if (status.usageStats) Icons.Default.CheckCircle else Icons.Default.Warning,
                if (status.usageStats) StatusTone.NEUTRAL else StatusTone.WARNING) })
        Text(stringResource(R.string.self_grants_description), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!status.secureSettings || !status.usageStats) {
            Button(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), enabled = !busy, onClick = {
                if (ShizukuPermission.isCantaAuthorized()) grant() else connect = true
            }) { Text(stringResource(R.string.self_grants_action)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        if (failed > 0) Text(stringResource(R.string.operation_failure_count, failed),
            Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
        result?.takeIf { it.isNotBlank() }?.let { Text(it, Modifier.padding(horizontal = 16.dp)) }
    }
    if (connect) ShizukuRequirementDialog { authorized ->
        connect = false
        if (authorized) grant()
    }
}
