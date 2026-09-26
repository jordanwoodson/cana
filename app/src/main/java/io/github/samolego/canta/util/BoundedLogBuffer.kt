package io.github.samolego.canta.util

class BoundedLogBuffer(
    private val maxEntries: Int = 500,
    private val maxTagChars: Int = 128,
    private val maxMessageChars: Int = 4096,
) {
    data class Entry(val level: Int, val tag: String, val message: String, val timestamp: Long)
    private val entries = ArrayDeque<Entry>()

    init { require(maxEntries > 0 && maxTagChars > 0 && maxMessageChars > 0) }

    @Synchronized
    fun append(level: Int, tag: String, message: String, timestamp: Long): Entry {
        val entry = Entry(level, truncate(tag, maxTagChars), truncate(message, maxMessageChars), timestamp)
        if (entries.size == maxEntries) entries.removeFirst()
        entries.addLast(entry)
        return entry
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    private fun truncate(value: String, maxChars: Int): String = if (value.length <= maxChars) value
        else value.take(maxChars - 1).dropLastWhile { it.isHighSurrogate() } + "…"
}
