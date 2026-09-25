package io.github.samolego.canta.ops

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Initializes sticky binder reconciliation even if Cana was not open at boot. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        CanaServices.initialize(context)
        CanaServices.getInstance().onSystemEvent()
    }
}
