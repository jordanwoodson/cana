package io.github.samolego.canta.util

object BloatUpdatePolicy {
    fun shouldFetch(
        nowMs: Long,
        lastCheckedMs: Long,
        autoUpdate: Boolean,
        forceRefresh: Boolean,
        unmeteredOnly: Boolean,
        isMetered: Boolean,
    ): Boolean {
        if (unmeteredOnly && isMetered) return false
        if (forceRefresh) return true
        if (!autoUpdate) return false
        return lastCheckedMs <= 0 || nowMs < lastCheckedMs ||
            nowMs - lastCheckedMs >= 24 * 60 * 60 * 1000L
    }
}
