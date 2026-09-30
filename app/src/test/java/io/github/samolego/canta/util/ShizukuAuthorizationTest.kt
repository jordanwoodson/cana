package io.github.samolego.canta.util

import io.github.samolego.canta.util.shizuku.AuthorizationState.*
import io.github.samolego.canta.util.shizuku.ShizukuAuthorization
import org.junit.Assert.*
import org.junit.Test

class ShizukuAuthorizationTest {
    @Test fun repeatedTapsAndResumeKeepOnePendingRequest() {
        val requests = mutableListOf<Int>()
        val auth = ShizukuAuthorization({ READY }, requests::add)
        auth.request()
        auth.refresh()
        auth.request()
        assertEquals(REQUESTING, auth.state.value)
        assertEquals(1, requests.size)
    }

    @Test fun denialStaysVisibleAndCanBeRetriedWithoutOldResultsCompletingNewRequest() {
        val requests = mutableListOf<Int>()
        var live = READY
        val auth = ShizukuAuthorization({ live }, requests::add)
        auth.request()
        val first = requests.single()
        auth.onResult(first, false)
        auth.refresh()
        assertEquals(DENIED, auth.state.value)
        auth.request()
        val second = requests.last()
        assertNotEquals(first, second)
        auth.onResult(first, true)
        assertEquals(REQUESTING, auth.state.value)
        live = GRANTED
        auth.onResult(second, true)
        assertEquals(GRANTED, auth.state.value)
    }

    @Test fun binderDeathClearsPendingRequestAndRejectsItsLateCallback() {
        val requests = mutableListOf<Int>()
        var live = READY
        val auth = ShizukuAuthorization({ live }, requests::add)
        auth.request()
        live = DISCONNECTED
        auth.refresh()
        auth.onResult(requests.single(), true)
        assertEquals(DISCONNECTED, auth.state.value)
        live = READY
        auth.refresh()
        auth.request()
        assertEquals(2, requests.size)
        assertEquals(REQUESTING, auth.state.value)
    }

    @Test fun unavailableUnsupportedBlockedAndGrantedNeverLaunchPermissionPrompts() {
        for (live in listOf(DISCONNECTED, UNSUPPORTED, BLOCKED, GRANTED)) {
            val auth = ShizukuAuthorization({ live }, { fail("Unexpected request for $live") })
            auth.request()
            assertEquals(live, auth.state.value)
        }
    }

    @Test fun blockedDenialDirectsToSettingsAndExternalGrantIsObservedOnResume() {
        var live = READY
        var code = 0
        val auth = ShizukuAuthorization({ live }, { code = it })
        auth.request()
        live = BLOCKED
        auth.onResult(code, false)
        assertEquals(BLOCKED, auth.state.value)
        live = GRANTED
        auth.refresh()
        assertEquals(GRANTED, auth.state.value)
    }

    @Test fun requestExceptionsAndTimeoutAllowRetryAndIgnoreExpiredCallbacks() {
        var fails = true
        val requests = mutableListOf<Int>()
        val auth = ShizukuAuthorization({ READY }, {
            if (fails) throw IllegalStateException("Binder died")
            requests.add(it)
        })
        auth.request()
        assertEquals(ERROR, auth.state.value)
        fails = false
        auth.request()
        val expired = requests.single()
        auth.onTimeout(expired)
        auth.refresh()
        assertEquals(TIMED_OUT, auth.state.value)
        auth.request()
        auth.onTimeout(expired)
        auth.onResult(expired, true)
        assertEquals(REQUESTING, auth.state.value)
        assertEquals(2, requests.size)
    }

    @Test fun failedStatusReadDoesNotCrashOrLeavePromptPending() {
        var fails = false
        val auth = ShizukuAuthorization({ if (fails) error("Disconnected during read") else READY }, {})
        auth.request()
        fails = true
        auth.refresh()
        assertEquals(ERROR, auth.state.value)
    }

    @Test fun grantCallbackMustStillHaveLiveAuthorization() {
        var code = 0
        val auth = ShizukuAuthorization({ DISCONNECTED }, { code = it })
        auth.onResult(code, true)
        assertEquals(DISCONNECTED, auth.state.value)
        var live = READY
        val active = ShizukuAuthorization({ live }, { code = it })
        active.request()
        live = DISCONNECTED
        active.onResult(code, true)
        assertEquals(DISCONNECTED, active.state.value)
    }

    @Test fun delayedCallbackAfterTimeoutObservesLiveGrantWithoutTrustingOldResult() {
        var live = READY
        var code = 0
        val auth = ShizukuAuthorization({ live }, { code = it })
        auth.request()
        auth.onTimeout(code)
        live = GRANTED
        auth.onResult(code, false)
        assertEquals(GRANTED, auth.state.value)
    }
}
