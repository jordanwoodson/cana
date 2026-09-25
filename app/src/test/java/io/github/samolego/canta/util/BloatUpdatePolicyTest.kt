package io.github.samolego.canta.util

import org.junit.Assert.*
import org.junit.Test

class BloatUpdatePolicyTest {
    private val now = 2_000_000_000L

    private fun shouldFetch(
        last: Long = now - 1,
        auto: Boolean = true,
        force: Boolean = false,
        unmeteredOnly: Boolean = false,
        metered: Boolean = false,
    ) = BloatUpdatePolicy.shouldFetch(now, last, auto, force, unmeteredOnly, metered)

    @Test fun automaticChecksAreLimitedToOncePerDay() {
        assertFalse(shouldFetch())
        assertFalse(shouldFetch(last = now - 86_399_999L))
        assertTrue(shouldFetch(last = now - 86_400_000L))
        assertTrue(shouldFetch(last = 0))
    }

    @Test fun manualRefreshBypassesAgeAndAutomaticSetting() {
        assertTrue(shouldFetch(force = true, auto = false))
        assertFalse(shouldFetch(last = 0, auto = false))
    }

    @Test fun unmeteredPreferenceAlsoAppliesToManualRefresh() {
        assertFalse(shouldFetch(last = 0, unmeteredOnly = true, metered = true))
        assertFalse(shouldFetch(force = true, unmeteredOnly = true, metered = true))
        assertTrue(shouldFetch(last = 0, unmeteredOnly = true, metered = false))
    }

    @Test fun clockRollbackDoesNotDisableUpdatesForever() {
        assertTrue(shouldFetch(last = now + 10_000L))
    }
}
