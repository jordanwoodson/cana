package io.github.samolego.canta.ops

import android.content.Context
import android.content.pm.PackageManager
import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.data.historyDataStore
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class CanaServices private constructor(context: Context) {
    val history = HistoryStore(context.applicationContext.historyDataStore)
    val shell = ShellRunner(context.applicationContext)
    val safety = SafetyInspector(context.applicationContext, shell)
    val packageOps = PackageOps(context.applicationContext, history, safety)
    val selfGrants = SelfGrants(context.applicationContext, shell, history)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        Shizuku.addBinderReceivedListenerSticky { ensureGrants() }
        Shizuku.addRequestPermissionResultListener { _, result ->
            if (result == PackageManager.PERMISSION_GRANTED) ensureGrants()
        }
    }

    private fun ensureGrants() {
        scope.launch {
            try {
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    selfGrants.grantMissing()
                }
            } catch (e: Exception) {
                LogUtils.e("CanaServices", "Could not check self grants", e)
            }
        }
    }

    companion object {
        @Volatile private var instance: CanaServices? = null
        fun initialize(context: Context) {
            synchronized(this) { if (instance == null) instance = CanaServices(context.applicationContext) }
        }
        fun getInstance(): CanaServices = checkNotNull(instance) { "CanaServices not initialized" }
    }
}
