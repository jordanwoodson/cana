package io.github.samolego.canta.ops

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val withDismiss: Boolean = false,
)

/** App-level feedback survives navigation because the producer does not hold a screen's SnackbarHost. */
class UiMessages {
    private val mutableMessages = MutableSharedFlow<UiMessage>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages = mutableMessages.asSharedFlow()

    fun post(message: UiMessage) { mutableMessages.tryEmit(message) }

    fun post(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, withDismiss: Boolean = false) {
        post(UiMessage(text, actionLabel, onAction, withDismiss))
    }
}
