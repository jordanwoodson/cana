package io.github.samolego.canta.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TimedCacheTest {
    private var now = 1_000L
    private val cache = TimedCache<Int, String>(ttlMs = 100) { now }
    private var computed = 0
    private suspend fun value(user: Int) = cache.getOrCompute(user) { computed++; "user-$user-$computed" }

    @Test fun reusesValueInsideWindowPerKey() = runBlocking {
        assertEquals("user-10-1", value(10))
        now += 99
        assertEquals("user-10-1", value(10))
        assertEquals("user-0-2", value(0))
        assertEquals("user-10-1", value(10))
        assertEquals(2, computed)
    }

    @Test fun recomputesAfterWindowOrClockGoingBackwards() = runBlocking {
        value(10)
        now += 100
        assertEquals("user-10-2", value(10))
        now -= 1
        assertEquals("user-10-3", value(10))
    }

    @Test fun invalidateDropsEveryEntry() = runBlocking {
        value(10); value(0)
        cache.invalidate()
        assertEquals("user-10-3", value(10))
        assertEquals("user-0-4", value(0))
    }

    @Test fun failuresAreNotCached() = runBlocking {
        val failed = runCatching { cache.getOrCompute(10) { error("shell unavailable") } }
        assertTrue(failed.isFailure)
        assertEquals("user-10-1", value(10))
    }
}
