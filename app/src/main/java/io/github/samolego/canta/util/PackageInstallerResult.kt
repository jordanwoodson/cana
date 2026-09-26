package io.github.samolego.canta.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * Waits for the status PackageInstaller reports through an [IntentSender], so that failures (e.g.
 * a work profile admin blocking uninstalls) aren't treated as success.
 */
object PackageInstallerResult {
    private const val TIMEOUT_SECONDS = 30L

    private val requestCodes = AtomicInteger()

    // Receive on a separate thread so waiting never blocks delivery
    private val handler by lazy {
        Handler(HandlerThread("CanaInstallerResult").apply { start() }.looper)
    }

    data class Result(val success: Boolean, val message: String?, val outcomeUnknown: Boolean = false, val requestId: String = "")
    private val outstanding = ConcurrentHashMap<String, InstallerCompletion>()
    /** A late callback requests read-only reconciliation, never a replay. */
    @Volatile var onLateCompletion: (() -> Unit)? = null
    fun terminalResult(requestId: String): Result? = outstanding[requestId]?.terminalResult()
    fun isAwaiting(requestId: String): Boolean = outstanding[requestId]?.terminalResult() == null && outstanding.containsKey(requestId)
    fun forget(requestId: String) { outstanding.remove(requestId) }

    /**
     * Calls [action] with an [IntentSender] to pass to PackageInstaller and blocks until the
     * status arrives or [TIMEOUT_SECONDS] pass.
     */
    fun await(context: Context, action: (IntentSender) -> Unit): Result {
        val requestCode = requestCodes.incrementAndGet()
        val intentAction = "${context.packageName}.INSTALLER_RESULT.$requestCode"
        val requestId = UUID.randomUUID().toString()
        val completion = InstallerCompletion(requestId)
        outstanding[requestId] = completion
        val returnedUnknown = AtomicBoolean(false)
        val unregistered = AtomicBoolean(false)
        var callbackIntent: PendingIntent? = null
        var dispatched = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val status = intent.getIntExtra(
                    PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE
                )
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) return
                completion.complete(Result(
                    success = status == PackageInstaller.STATUS_SUCCESS,
                    message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                ))
                if (unregistered.compareAndSet(false, true)) context.unregisterReceiver(this)
                callbackIntent?.cancel()
                if (returnedUnknown.get()) runCatching { onLateCompletion?.invoke() }
            }
        }
        try { ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(intentAction),
            null,
            handler,
            ContextCompat.RECEIVER_NOT_EXPORTED
        ) } catch (e: Exception) { outstanding.remove(requestId); throw e }

        try {
            // Must be mutable so PackageInstaller can fill in the status extras
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(intentAction).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable
            )
            callbackIntent = pendingIntent

            dispatched = true
            try { action(pendingIntent.intentSender) }
            catch (e: Exception) {
                returnedUnknown.set(true)
                return Result(false, e.message, outcomeUnknown = true, requestId = requestId)
            }
            val result = completion.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (result.outcomeUnknown) {
                returnedUnknown.set(true)
                if (completion.terminalResult() != null) runCatching { onLateCompletion?.invoke() }
            } else {
                outstanding.remove(requestId)
                pendingIntent.cancel()
            }
            return result
        } finally {
            // Keep the receiver for an outstanding request so a late terminal result can be reconciled.
            if (!dispatched || completion.terminalResult() != null) {
                if (unregistered.compareAndSet(false, true)) context.unregisterReceiver(receiver)
                callbackIntent?.cancel()
            }
            if (!dispatched) outstanding.remove(requestId)
        }
    }
}

/** Injectable completion boundary: timeout does not mean Android stopped the request. */
internal class InstallerCompletion(private val requestId: String) {
    private val latch = CountDownLatch(1)
    @Volatile private var result: PackageInstallerResult.Result? = null
    @Synchronized fun complete(value: PackageInstallerResult.Result) {
        if (result != null) return
        require(!value.outcomeUnknown)
        result = value
        latch.countDown()
    }
    fun terminalResult(): PackageInstallerResult.Result? = result
    fun await(timeout: Long, unit: TimeUnit): PackageInstallerResult.Result {
        try { latch.await(timeout, unit) }
        catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        return result ?: PackageInstallerResult.Result(false, "PackageInstaller is still pending; review recovery before retrying",
            outcomeUnknown = true, requestId = requestId)
    }
}
