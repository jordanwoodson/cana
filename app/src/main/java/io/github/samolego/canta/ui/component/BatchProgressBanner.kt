package io.github.samolego.canta.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
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
@OptIn(ExperimentalLayoutApi::class)
fun BatchProgressBanner(onHistory: () -> Unit) {
    val services = CanaServices.getInstance()
    val active by services.batches.active.collectAsStateWithLifecycle()
    val stored by services.batchStore.state.collectAsStateWithLifecycle(io.github.samolego.canta.data.BatchReadState())
    val batches = stored.batches
    val history by services.history.state.collectAsStateWithLifecycle(io.github.samolego.canta.data.HistoryReadState())
    var stopFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val unfinished = batches.any { it.interrupted && !it.reviewAcknowledged }
    val pending = history.needsRecoveryReview
    val current = active
    if (current != null || unfinished || pending || stored.unavailable || history.unavailable) CanaBanner(
        icon = if (current == null) Icons.Default.Warning else Icons.Default.Info,
        text = if (current != null) stringResource(R.string.batch_progress, current.title, current.completed, current.total)
            else stringResource(if (history.unavailable) R.string.history_store_unavailable else if (stored.unavailable) R.string.batch_store_unavailable else if (pending) R.string.history_pending_review else R.string.batch_interrupted),
        tone = if (stored.unavailable || history.unavailable || stopFailed) StatusTone.ERROR
            else if (current == null) StatusTone.WARNING else StatusTone.NEUTRAL,
        modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
        details = {
            if (current != null) LinearProgressIndicator(progress = { if (current.total == 0) 0f else current.completed.toFloat() / current.total }, modifier = Modifier.fillMaxWidth())
            if (stopFailed) Text(stringResource(R.string.batch_stop_failed), color = MaterialTheme.colorScheme.error)
            FlowRow {
                TextButton(onClick = onHistory) { Text(stringResource(R.string.batch_review)) }
                if (current != null) TextButton(enabled = !current.stopping, onClick = {
                    scope.launch { stopFailed = !services.batches.requestStop(current.id) }
                }) { Text(stringResource(if (current.stopping) R.string.batch_stopping else R.string.batch_stop)) }
            }
        },
    )
}
