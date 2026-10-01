package io.github.samolego.canta.ops

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiMessagesTest {
    @Test fun completionFromAnIndependentProducerReachesTheActiveCollector() = runBlocking {
        val bus = UiMessages()
        val received = async(start = CoroutineStart.UNDISPATCHED) { bus.messages.first() }
        var acted = false
        bus.post(UiMessage("Saved", "Undo", onAction = { acted = true }, withDismiss = true))
        val message = received.await()
        assertEquals("Saved", message.text)
        assertEquals("Undo", message.actionLabel)
        assertTrue(message.withDismiss)
        message.onAction?.invoke()
        assertTrue(acted)
    }
}
