package io.github.samolego.canta.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.samolego.canta.ops.UiMessages

val LocalCanaSnackbar = staticCompositionLocalOf<SnackbarHostState> {
    error("Cana snackbar host is only available inside CantaApp")
}

@Composable
fun CanaMessageCollector(messages: UiMessages, hostState: SnackbarHostState) {
    LaunchedEffect(messages, hostState) {
        messages.messages.collect { message ->
            val result = hostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                withDismissAction = message.withDismiss,
                duration = if (message.actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
        }
    }
}
