package io.github.samolego.canta.util.shizuku

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthorizationState {
    DISCONNECTED, UNSUPPORTED, READY, REQUESTING, GRANTED, DENIED, BLOCKED, ERROR, TIMED_OUT,
}

/** Main-thread request state; platform calls and deadlines are supplied by the Android adapter. */
internal class ShizukuAuthorization(
    private val readState: () -> AuthorizationState,
    private val requestPermission: (Int) -> Unit,
) {
    private val mutableState = MutableStateFlow(AuthorizationState.DISCONNECTED)
    val state = mutableState.asStateFlow()
    private var nextRequestCode = 0xCA07A
    private var pendingRequestCode: Int? = null

    private fun liveState() = try { readState() } catch (_: Exception) { AuthorizationState.ERROR }

    fun refresh() {
        val live = liveState()
        mutableState.value = if (live == AuthorizationState.READY) {
            when {
                pendingRequestCode != null -> AuthorizationState.REQUESTING
                state.value in setOf(AuthorizationState.DENIED, AuthorizationState.ERROR, AuthorizationState.TIMED_OUT) -> state.value
                else -> live
            }
        } else {
            pendingRequestCode = null
            live
        }
    }

    fun request() {
        refresh()
        if (state.value !in setOf(AuthorizationState.READY, AuthorizationState.DENIED,
                AuthorizationState.ERROR, AuthorizationState.TIMED_OUT)) return
        // A read failure is not evidence that a permission request is possible.
        if (liveState() != AuthorizationState.READY) return
        val code = nextRequestCode++
        pendingRequestCode = code
        mutableState.value = AuthorizationState.REQUESTING
        try { requestPermission(code) }
        catch (_: Exception) {
            if (pendingRequestCode == code) {
                pendingRequestCode = null
                mutableState.value = AuthorizationState.ERROR
            }
        }
    }

    fun onResult(requestCode: Int, granted: Boolean) {
        if (pendingRequestCode != requestCode) {
            // A delayed/foreign result cannot finish this request, but live permission may
            // have changed while the app remained resumed (e.g. with another manager).
            refresh()
            return
        }
        pendingRequestCode = null
        val live = liveState()
        mutableState.value = if (live == AuthorizationState.READY) {
            if (granted) AuthorizationState.ERROR else AuthorizationState.DENIED
        } else live
    }

    fun onTimeout(requestCode: Int) {
        if (pendingRequestCode != requestCode) return
        pendingRequestCode = null
        val live = liveState()
        mutableState.value = if (live == AuthorizationState.READY) AuthorizationState.TIMED_OUT else live
    }
}
