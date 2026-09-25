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

/**
 * Waits for the status PackageInstaller reports through an [IntentSender], so that failures (e.g.
 * a work profile admin blocking uninstalls) aren't treated as success.
 */
object PackageInstallerResult {
    private const val TIMEOUT_SECONDS = 30L

    private val requestCodes = AtomicInteger()

    // Receive on a separate thread so waiting never blocks delivery
    private val handler by lazy {
        Handler(HandlerThread("CantaInstallerResult").apply { start() }.looper)
    }

    data class Result(val success: Boolean, val message: String?)

    /**
     * Calls [action] with an [IntentSender] to pass to PackageInstaller and blocks until the
     * status arrives or [TIMEOUT_SECONDS] pass.
     */
    fun await(context: Context, action: (IntentSender) -> Unit): Result {
        val requestCode = requestCodes.incrementAndGet()
        val intentAction = "${context.packageName}.INSTALLER_RESULT.$requestCode"
        val latch = CountDownLatch(1)
        var result = Result(false, "No result from PackageInstaller within ${TIMEOUT_SECONDS}s")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val status = intent.getIntExtra(
                    PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE
                )
                result = Result(
                    success = status == PackageInstaller.STATUS_SUCCESS,
                    message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                )
                latch.countDown()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(intentAction),
            null,
            handler,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

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

            action(pendingIntent.intentSender)
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return result
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
