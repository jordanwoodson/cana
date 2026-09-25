package io.github.samolego.canta.ops

import android.content.Context
import android.content.pm.PackageManager
import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.data.historyDataStore
import io.github.samolego.canta.data.PrivacyStore
import io.github.samolego.canta.data.privacyDataStore
import io.github.samolego.canta.data.ManagementStore
import io.github.samolego.canta.data.managementDataStore
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.TrackerRepository
import kotlinx.coroutines.CancellationException
import rikka.shizuku.Shizuku

class CanaServices private constructor(context: Context) {
    private val appContext = context.applicationContext
    val history = HistoryStore(context.applicationContext.historyDataStore)
    val shell = ShellRunner(context.applicationContext)
    val safety = SafetyInspector(context.applicationContext, shell)
    val packageOps = PackageOps(context.applicationContext, history, safety, shell)
    val selfGrants = SelfGrants(context.applicationContext, shell, history)
    val trackers = TrackerRepository(context.applicationContext)
    val components = ComponentRepository(trackers)
    val desiredPrivacy = PrivacyStore(context.applicationContext.privacyDataStore)
    val journal = OperationJournal(context.applicationContext, history)
    val privacy = PrivacyOps(context.applicationContext, shell, history, desiredPrivacy, safety, journal)
    val presets = PresetOps(packageOps, privacy, history)
    val undo = UndoCoordinator(history, packageOps, privacy)
    val management = ManagementStore(appContext.managementDataStore)
    val ota = OtaRepository(appContext, management, history)

    init {
        Shizuku.addBinderReceivedListenerSticky { ensureGrants() }
        Shizuku.addRequestPermissionResultListener { _, result ->
            if (result == PackageManager.PERMISSION_GRANTED) ensureGrants()
        }
    }

    private fun ensureGrants() {
        ReconcileJobService.schedule(appContext)
    }

    suspend fun reconcileNow() {
        try { ota.check() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { LogUtils.e("CanaServices", "Could not check system update changes", e) }
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                selfGrants.grantMissing()
                privacy.reapplyDesired()
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { LogUtils.e("CanaServices", "Could not reconcile grants and desired state", e) }
    }

    fun onSystemEvent() { ensureGrants() }

    companion object {
        @Volatile private var instance: CanaServices? = null
        fun initialize(context: Context) {
            synchronized(this) { if (instance == null) instance = CanaServices(context.applicationContext) }
        }
        fun getInstance(): CanaServices = checkNotNull(instance) { "CanaServices not initialized" }
    }
}
