package io.github.samolego.canta.ops

import android.content.Context
import android.content.pm.PackageManager
import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.data.HistoryArchive
import io.github.samolego.canta.data.historyDataStore
import io.github.samolego.canta.data.PrivacyStore
import io.github.samolego.canta.data.privacyDataStore
import io.github.samolego.canta.data.ManagementStore
import io.github.samolego.canta.data.managementDataStore
import io.github.samolego.canta.data.batchDataStore
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.TrackerRepository
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import rikka.shizuku.Shizuku
import java.io.File

class CanaServices private constructor(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val uiMessages = UiMessages()
    val batchStore = io.github.samolego.canta.data.BatchStore(appContext.batchDataStore)
    val profiles = ProfileDirectory()
    val inventory = InventoryRepository(appContext, scope)
    val shell = ShellRunner()
    val safety = SafetyInspector(context.applicationContext, shell)
    // A full package scan per item would compete with the batch itself; scan once at the end.
    val batches = BatchCoordinator(batchStore, scope, BatchMessages(
        appContext.getString(io.github.samolego.canta.R.string.batch_stopped_item),
        appContext.getString(io.github.samolego.canta.R.string.batch_operation_failed),
        appContext.getString(io.github.samolego.canta.R.string.batch_storage_failed),
    ), onAttemptFinished = { inventory.invalidate(refresh = false) },
        onBatchFinished = { safety.invalidate(); inventory.invalidate() })
    val savedViews = io.github.samolego.canta.data.SavedViewStore(appContext)
    private val installationId = OperationalIdentity.current(appContext)
    val history = HistoryStore(appContext.historyDataStore,
        HistoryArchive(File(appContext.noBackupFilesDir, "history-archive")), installationId = installationId)
    val packageOps = PackageOps(context.applicationContext, history, safety, shell)
    val selfGrants = SelfGrants(context.applicationContext, shell, history)
    val trackers: TrackerRepository = TrackerRepository(context.applicationContext, onSourceChanged = {
        scope.launch { trackerScans.invalidate() }
    })
    val components = ComponentRepository(trackers)
    val trackerScans = TrackerScanRepository(appContext, scope, trackers::load)
    val desiredPrivacy = PrivacyStore(context.applicationContext.privacyDataStore)
    val journal = OperationJournal(context.applicationContext, history)
    val privacy = PrivacyOps(context.applicationContext, shell, history, desiredPrivacy, safety, journal)
    val presets = PresetOps(packageOps, privacy, history, batches)
    val system = SystemOps(appContext, shell, history, journal)
    val undo = UndoCoordinator(history, packageOps, privacy, system, batches)
    val management = ManagementStore(appContext.managementDataStore)
    val ota = OtaRepository(appContext, management, history)

    init {
        scope.launch { if (ShizukuPermission.isCantaAuthorized()) profiles.refresh() }
        io.github.samolego.canta.util.PackageInstallerResult.onLateCompletion = { ensureGrants() }
        scope.launch {
            try { history.bindInstallation(installationId) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { LogUtils.e("CanaServices", "Could not validate operation history installation", e) }
            try { privacy.initialize() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { LogUtils.e("CanaServices", "Could not validate saved privacy installation", e) }
        }
        Shizuku.addBinderDeadListener { scope.launch {
            try { privacy.markDisconnected() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { LogUtils.e("CanaServices", "Could not update disconnected privacy state", e) }
        } }
        scope.launch {
            var observed: Set<String>? = null
            combine(history.state, batchStore.state) { history, batches -> history to batches }
                .collect { (state, batches) ->
                    if (state.unavailable) return@collect
                    val records = state.records
                    val completed = records.filter { it.completed }.map { it.id }.toSet()
                    if (observed != null && (completed - observed!!).isNotEmpty()) inventory.invalidate(refresh = this@CanaServices.batches.active.value == null)
                    try { if (batches.batches.any { batch -> batch.itemsList.any { it.status == "pending" } }) batchStore.reconcilePending(records) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { LogUtils.e("CanaServices", "Could not update pending batch outcomes", e) }
                    observed = completed
                }
        }
        Shizuku.addBinderReceivedListenerSticky {
            ensureGrants()
            scope.launch { if (ShizukuPermission.isCantaAuthorized()) profiles.refresh() }
        }
        Shizuku.addRequestPermissionResultListener { _, result ->
            if (result == PackageManager.PERMISSION_GRANTED) {
                ensureGrants()
                scope.launch { profiles.refresh() }
            }
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
                packageOps.reconcilePending()
                privacy.reapplyDesired()
            } else privacy.markDisconnected()
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
