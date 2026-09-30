package io.github.samolego.canta.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Remembers one successfully computed value per key for a short window. A failed computation is
 * never cached, and [invalidate] drops every entry, including one being computed right now.
 */
class TimedCache<K, V>(private val ttlMs: Long, private val clock: () -> Long = System::currentTimeMillis) {
    init { require(ttlMs > 0) }

    private class Entry<V>(val value: V, val createdAt: Long, val generation: Long)

    private val mutex = Mutex()
    private val entries = HashMap<K, Entry<V>>()
    private val generation = AtomicLong()

    suspend fun getOrCompute(key: K, compute: suspend () -> V): V = mutex.withLock {
        val now = clock()
        val current = generation.get()
        entries[key]?.takeIf { it.generation == current && now - it.createdAt in 0 until ttlMs }
            ?.let { return@withLock it.value }
        compute().also { entries[key] = Entry(it, now, current) }
    }

    fun invalidate() { generation.incrementAndGet() }
}
