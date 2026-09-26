package io.github.samolego.canta.util

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BoundedLogBufferTest {
    @Test fun retainsNewestEntriesAndPreviouslyReadSnapshotsRemainStable() {
        val buffer = BoundedLogBuffer(maxEntries = 2)
        buffer.append(1, "tag", "first", 1)
        val previous = buffer.snapshot()
        buffer.append(1, "tag", "second", 2)
        buffer.append(1, "tag", "third", 3)
        assertEquals(listOf("second", "third"), buffer.snapshot().map { it.message })
        assertEquals(listOf("first"), previous.map { it.message })
    }

    @Test fun capsTagsAndMessagesBeforeRetainingThem() {
        val buffer = BoundedLogBuffer(maxEntries = 3, maxTagChars = 4, maxMessageChars = 8)
        val entry = buffer.append(2, "oversized tag", "oversized diagnostic message", 50)
        assertEquals("ove…", entry.tag)
        assertEquals("oversiz…", entry.message)
        assertEquals(50L, entry.timestamp)
        assertEquals(entry, buffer.snapshot().single())
    }

    @Test fun concurrentWritersCannotExceedTheBoundOrLoseCompleteEntryValues() {
        val buffer = BoundedLogBuffer(maxEntries = 25)
        val writers = Executors.newFixedThreadPool(4)
        repeat(200) { id -> writers.submit { buffer.append(1, "tag$id", "message$id", id.toLong()) } }
        writers.shutdown()
        assertTrue(writers.awaitTermination(5, TimeUnit.SECONDS))
        val entries = buffer.snapshot()
        assertEquals(25, entries.size)
        assertEquals(25, entries.map { it.timestamp }.toSet().size)
        entries.forEach { assertEquals("message${it.timestamp}", it.message) }
    }
}
