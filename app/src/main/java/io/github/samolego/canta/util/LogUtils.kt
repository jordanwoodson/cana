package io.github.samolego.canta.util

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object LogUtils {
    private val buffer = BoundedLogBuffer()
    private var entries by mutableStateOf<List<LogEntry>>(emptyList())
    private val dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US).withZone(ZoneId.systemDefault())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // One pending notification avoids an unbounded coroutine queue during verbose operations.
    private val updates = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (signal in updates) entries = buffer.snapshot().map {
                LogEntry(LogLevel.entries[it.level], it.tag, it.message, it.timestamp)
            }
        }
    }

    fun d(tag: String, message: String) {
        addLog(LogLevel.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        addLog(LogLevel.INFO, tag, message)
    }

    fun w(tag: String, message: String) {
        addLog(LogLevel.WARNING, tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        addLog(LogLevel.ERROR, tag, message + (throwable?.message?.let { "\n$it" } ?: ""), throwable)
    }

    private fun addLog(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = buffer.append(level.ordinal, tag, message, System.currentTimeMillis())
        when (level) {
            LogLevel.DEBUG -> Log.d(entry.tag, entry.message)
            LogLevel.INFO -> Log.i(entry.tag, entry.message)
            LogLevel.WARNING -> Log.w(entry.tag, entry.message)
            LogLevel.ERROR -> Log.e(entry.tag, entry.message, throwable)
        }
        updates.trySend(Unit)
    }

    fun getLogs(): List<LogEntry> = entries

    data class LogEntry(
            val level: LogLevel,
            val tag: String,
            val message: String,
            val timestamp: Long
    ) {
        fun getFormattedTime(): String = dateFormat.format(Instant.ofEpochMilli(timestamp))
    }

    enum class LogLevel(val color: Color) {
        DEBUG(Color.Gray),
        INFO(Color.Green),
        WARNING(Color.Yellow),
        ERROR(Color.Red)
    }
}
