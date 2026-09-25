package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import kotlinx.coroutines.launch

@Composable
fun SelfGrantSettings() {
    val grants = remember { CanaServices.getInstance().selfGrants }
    val status by grants.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { grants.refreshStatus() }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.self_grants_title))
        Text(stringResource(R.string.self_grant_secure_settings,
            stringResource(if (status.secureSettings) R.string.grant_allowed else R.string.grant_missing)))
        Text(stringResource(R.string.self_grant_usage_stats,
            stringResource(if (status.usageStats) R.string.grant_allowed else R.string.grant_missing)))
        Text(stringResource(R.string.self_grants_description))
        if (!status.secureSettings || !status.usageStats) {
            Button(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        val results = grants.grantMissing()
                        failed = results.count { !it.success }
                        result = results.filterNot { it.success }.joinToString("\n") { it.message }
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.self_grants_action)) }
        }
        if (failed > 0) Text(stringResource(R.string.operation_failure_count, failed))
        result?.takeIf { it.isNotBlank() }?.let { Text(it) }
    }
}
