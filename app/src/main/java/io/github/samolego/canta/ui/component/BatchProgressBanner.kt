package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.CanaServices
import kotlinx.coroutines.launch

/** Lives above navigation, so progress and recovery survive leaving an initiating screen. */
@Composable
fun BatchProgressBanner(onHistory: () -> Unit) {
    val services = CanaServices.getInstance()
    val active by services.batches.active.collectAsStateWithLifecycle()
    val stored by services.batchStore.state.collectAsStateWithLifecycle(io.github.samolego.canta.data.BatchReadState())
    val batches = stored.batches
    val history by services.history.state.collectAsStateWithLifecycle(io.github.samolego.canta.data.HistoryReadState())
    val records = history.records
    var stopFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val unfinished = batches.any { it.interrupted && !it.reviewAcknowledged }
    val pending = records.any { !it.completed }
    val current = active
    if (current != null || unfinished || pending || stored.unavailable || history.unavailable) Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(if (current != null) stringResource(R.string.batch_progress, current.title, current.completed, current.total)
                else stringResource(if (history.unavailable) R.string.history_store_unavailable else if (stored.unavailable) R.string.batch_store_unavailable else if (pending) R.string.recovery_pending_review else R.string.batch_interrupted), style = MaterialTheme.typography.labelLarge)
            if (current != null) LinearProgressIndicator(progress = { current.completed.toFloat() / current.total }, modifier = Modifier.fillMaxWidth())
            if (stopFailed) Text(stringResource(R.string.batch_stop_failed), color = MaterialTheme.colorScheme.error)
            Row {
                TextButton(onClick = onHistory) { Text(stringResource(R.string.batch_review)) }
                if (current != null) TextButton(enabled = !current.stopping, onClick = {
                    scope.launch { stopFailed = !services.batches.requestStop(current.id) }
                }) { Text(stringResource(if (current.stopping) R.string.batch_stopping else R.string.batch_stop)) }
            }
        }
    }
}
