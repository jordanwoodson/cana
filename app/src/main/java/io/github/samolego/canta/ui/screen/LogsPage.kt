package io.github.samolego.canta.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ui.component.IconClickButton
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.EmptyState
import io.github.samolego.canta.util.LogUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsPage(onNavigateBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val logs = LogUtils.getLogs()
    val copiedText = stringResource(R.string.log_copied)
    val copyAll = {
        val logText = logs.joinToString("\n") { log ->
            "[${log.getFormattedTime()}] ${log.level} ${log.tag}: ${log.message}"
        }
        clipboardManager.setText(AnnotatedString(logText))
        CanaServices.getInstance().uiMessages.post(copiedText)
    }

    Scaffold(
            snackbarHost = { androidx.compose.material3.SnackbarHost(io.github.samolego.canta.ui.LocalCanaSnackbar.current) },
            topBar = {
                ScreenTopBar(onNavigateBack, { Text(stringResource(R.string.logs)) },
                    actions = { IconClickButton(onClick = copyAll, icon = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy_logs)) })
            }
    ) { padding ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(padding)
                                .verticalScroll(rememberScrollState())
        ) {
            if (logs.isEmpty()) EmptyState(Icons.Default.Description,
                stringResource(R.string.logs_empty_title), stringResource(R.string.logs_empty_body))
            else logs.forEach { logEntry -> LogEntryChip(logEntry) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogEntryChip(logEntry: LogUtils.LogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val copiedText = stringResource(R.string.log_copied)
    val uiMessages = remember { CanaServices.getInstance().uiMessages }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .padding(horizontal = 8.dp)
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = {
                    val logText = "[${logEntry.getFormattedTime()}] ${logEntry.level} ${logEntry.tag}: ${logEntry.message}"
                    clipboardManager.setText(AnnotatedString(logText))
                    uiMessages.post(copiedText)
                }
            ),
        color = when (logEntry.level) {
            LogUtils.LogLevel.DEBUG -> MaterialTheme.colorScheme.surfaceVariant
            LogUtils.LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer
            LogUtils.LogLevel.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
            LogUtils.LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${logEntry.level} ${logEntry.tag}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = logEntry.getFormattedTime(),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    text = logEntry.message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

}
