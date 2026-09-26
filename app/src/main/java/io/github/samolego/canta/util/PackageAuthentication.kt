package io.github.samolego.canta.util

import android.content.Context
import android.widget.Toast
import io.github.samolego.canta.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Read the durable preference before every attempt; loading/error never means unlocked. */
suspend fun withPackageAuthentication(context: Context, authenticate: suspend () -> Boolean = { requestAuthentication(context) }, action: suspend () -> Unit) = withContext(Dispatchers.Main) {
    try {
        authenticatedAction(SettingsStore.getInstance().authEnabledFlow.first(), authenticate, action)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        LogUtils.e("PackageAuthentication", "Authenticated operation failed", e)
        Toast.makeText(context, e.message ?: e.toString(), Toast.LENGTH_LONG).show()
    }
    Unit
}

private suspend fun requestAuthentication(context: Context): Boolean = suspendCancellableCoroutine { continuation ->
    val prompt = showBiometricPrompt(context,
        onError = { if (continuation.isActive) continuation.resume(false) },
        onSuccess = { if (continuation.isActive) continuation.resume(true) })
    continuation.invokeOnCancellation { prompt.cancelAuthentication() }
}
