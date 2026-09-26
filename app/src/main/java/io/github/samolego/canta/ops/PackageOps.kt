package io.github.samolego.canta.ops

import android.content.Context
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.IPackageInstaller
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import io.github.samolego.canta.R
import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.PackageInstallerResult
import io.github.samolego.canta.util.UninstallSequence
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils
import io.github.samolego.canta.util.shizuku.ShizukuUserUtils
import io.github.samolego.canta.util.apps.updateApkSize
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import java.util.UUID
import java.io.File

/** All mutations capture an explicit user and journal their before/after state. */
class PackageOps(context: Context, private val history: HistoryStore, private val safety: SafetyInspector, private val shell: ShellRunner) {
    private val context = context.applicationContext
    private val mutationMutex = Mutex()

    suspend fun inspectUpdates(packageName: String, userId: Int): UpdateImpact = withContext(Dispatchers.IO) {
        try { inspectUpdatesNow(packageName, userId) }
        catch (e: Exception) {
            UpdateImpact(packageName, userId, false, false, 0, emptyList(),
                (e.cause ?: e).message ?: context.getString(R.string.operation_failed))
        }
    }

    private fun inspectUpdatesNow(packageName: String, userId: Int): UpdateImpact {
        val app = getPackageInfo(packageName, userId)?.applicationInfo
            ?: error(context.getString(R.string.operation_package_missing))
        val profiles = ShizukuUserUtils.getUsers()
        check(profiles.any { it.id == userId }) { context.getString(R.string.profile_missing) }
        val others = profiles.filter { it.id != userId }.filter { profile ->
            val other = getPackageInfo(packageName, profile.id)?.applicationInfo
            other != null && other.flags and ApplicationInfo.FLAG_INSTALLED != 0
        }
        val updated = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return UpdateImpact(packageName, userId, updated, app.flags and ApplicationInfo.FLAG_INSTALLED != 0,
            updateApkSize(updated, app.sourceDir, app.splitSourceDirs?.toList().orEmpty()), others)
    }

    suspend fun canResetToFactory(packageName: String, userId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val info = getPackageInfo(packageName, userId)?.applicationInfo
            info != null && info.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        } catch (e: Exception) {
            LogUtils.e("PackageOps", "Cannot query $packageName for user $userId", e)
            false
        }
    }

    suspend fun uninstall(
        packageName: String,
        userId: Int,
        resetToFactory: Boolean = false,
        batchId: String = UUID.randomUUID().toString(),
        approvedDowngradeUsers: Set<Int> = emptySet(),
        approvedWarnings: Set<String> = emptySet(),
        keepData: Boolean = false,
    ): OperationResult = recorded(packageName, userId, if (keepData) "uninstall_keep_data" else "uninstall", batchId) { before ->
        safety.requireAllowed(packageName, userId, approvedWarnings, removing = true)
        val info = before?.applicationInfo ?: error(context.getString(R.string.operation_package_missing))
        check(info.flags and ApplicationInfo.FLAG_INSTALLED != 0) {
            context.getString(R.string.operation_already_uninstalled)
        }
        val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val updated = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        if (resetToFactory && system && updated) {
            check(inspectUpdatesNow(packageName, userId).otherInstalledProfiles.all { it.id in approvedDowngradeUsers }) {
                context.getString(R.string.update_profiles_changed)
            }
        }
        val result = UninstallSequence.execute(
            resetFirst = resetToFactory && !keepData && system && updated,
            uninstallFlags = (if (system) 4 else 0) or (if (keepData) 1 else 0),
            hasUpdates = {
                getPackageInfo(packageName, userId)?.applicationInfo?.let {
                    it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                }
            },
            uninstall = { uninstallStep(packageName, userId, it) },
        )
        if (!result.success) OperationResult(false, result.message ?: context.getString(R.string.operation_failed))
        else {
            val after = getPackageInfo(packageName, userId)?.applicationInfo
            val removed = after == null || after.flags and ApplicationInfo.FLAG_INSTALLED == 0
            OperationResult(removed, context.getString(if (removed) R.string.operation_success else R.string.operation_not_applied))
        }
    }

    suspend fun setEnabled(packageName: String, userId: Int, enabled: Boolean,
        approvedWarnings: Set<String> = emptySet(), batchId: String = UUID.randomUUID().toString(),
    ): OperationResult = recorded(packageName, userId, if (enabled) "enable" else "disable", batchId) {
        if (!enabled) safety.requireAllowed(packageName, userId, approvedWarnings)
        else safety.requireUnprotected(packageName, userId)
        val result = shell.exec(listOf("pm", if (enabled) "enable" else "disable-user", "--user", "$userId", packageName))
        val applied = ShizukuPackageInstallerUtils.applicationEnabledSetting(packageName, userId) == if (enabled) 1 else 3
        verified(result, applied)
    }

    suspend fun setSuspended(packageName: String, userId: Int, suspended: Boolean,
        approvedWarnings: Set<String> = emptySet(), batchId: String = UUID.randomUUID().toString(),
    ): OperationResult = recorded(packageName, userId, if (suspended) "suspend" else "unsuspend", batchId) {
        if (suspended) safety.requireAllowed(packageName, userId, approvedWarnings)
        else safety.requireUnprotected(packageName, userId)
        val result = shell.exec(listOf("pm", if (suspended) "suspend" else "unsuspend", "--user", "$userId", packageName))
        val actual = getPackageInfo(packageName, userId)?.applicationInfo?.let { it.flags and ApplicationInfo.FLAG_SUSPENDED != 0 }
        verified(result, actual == suspended)
    }

    suspend fun setComponentEnabled(component: ComponentName, userId: Int, enabled: Boolean,
        approvedWarnings: Set<String> = emptySet(), batchId: String = UUID.randomUUID().toString(),
    ): OperationResult = recorded(component.packageName, userId, "component", batchId,
        snapshotter = { info, user -> componentSnapshot(info, user, component) }) { before ->
        check(Shizuku.getUid() != 2000 || (before?.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_TEST_ONLY != 0) {
            context.getString(R.string.component_requires_root)
        }
        if (!enabled) safety.requireAllowed(component.packageName, userId, approvedWarnings)
        else safety.requireUnprotected(component.packageName, userId)
        val result = shell.exec(listOf("pm", if (enabled) "enable" else "disable", "--user", "$userId", component.flattenToString()))
        verified(result, ShizukuPackageInstallerUtils.componentEnabledSetting(component, userId) == if (enabled) 1 else 2)
    }

    /** Undo uses the recorded target/user, restores exact prior values, and is journaled itself. */
    suspend fun undo(record: OperationRecord, batchId: String = UUID.randomUUID().toString(),
        approvedWarnings: Set<String> = emptySet(),
    ): OperationResult {
        if (record.action !in setOf("uninstall", "uninstall_keep_data", "reinstall", "remove_updates", "disable", "enable", "suspend", "unsuspend", "component")) {
            return OperationResult(false, context.getString(R.string.undo_unsupported))
        }
        val known = history.records.first()
        if (known.none { it == record }) return OperationResult(false, context.getString(R.string.operation_history_failed))
        if (known.any { it.undoOf == record.id && it.completed && (it.success || it.recoveryComplete) }) {
            return OperationResult(true, context.getString(R.string.operation_skipped), skipped = true)
        }
        val prior = runCatching { JSONObject(record.previousState) }.getOrDefault(JSONObject())
        val component = prior.optString("component").takeIf { it.isNotBlank() }?.let(ComponentName::unflattenFromString)
        return recorded(record.packageName, record.userId, record.action, batchId, undoOf = record.id,
            snapshotter = { info, user -> if (component != null) componentSnapshot(info, user, component) else snapshot(info, user) }) {
            val plan = RestoreScript.plan(record)
            // A removed update payload needs a manual APK reinstall, not another shell mutation.
            if (record.action == "remove_updates" && plan.commands.isEmpty() &&
                prior.has("installed") && prior.optBoolean("updatedSystemApp") && plan.notes.isNotEmpty()) {
                return@recorded OperationResult(false, plan.notes.joinToString("\n"), recoveryComplete = true)
            }
            check(plan.commands.isNotEmpty()) { plan.notes.joinToString("\n").ifBlank { context.getString(R.string.operation_not_applied) } }
            if (plan.commands.any { it.getOrNull(1) in setOf("disable", "disable-user", "disable-until-used", "uninstall", "suspend") }) {
                safety.requireAllowed(record.packageName, record.userId, approvedWarnings,
                    removing = plan.commands.any { it.getOrNull(1) == "uninstall" })
            }
            val results = plan.commands.map { shell.exec(it) }
            val after = JSONObject(if (component != null) componentSnapshot(getPackageInfo(record.packageName, record.userId), record.userId, component)
                else snapshot(getPackageInfo(record.packageName, record.userId), record.userId))
            val keys = when (record.action) {
                "disable", "enable", "component" -> listOf("enabledSetting")
                "suspend", "unsuspend" -> listOf("suspended")
                "uninstall", "uninstall_keep_data", "reinstall", "remove_updates" -> listOf("installed", "enabledSetting")
                else -> emptyList()
            }
            val matches = keys.isNotEmpty() && keys.filter { prior.has(it) }.all { prior.get(it) == after.opt(it) }
            val recovered = results.all { it.success } && matches
            val success = recovered && plan.notes.isEmpty()
            val messages = results.filter { !it.success }.map { it.message } + plan.notes
            OperationResult(success, messages.joinToString("\n").ifBlank {
                context.getString(if (success) R.string.operation_success else R.string.operation_not_applied)
            }, recoveryComplete = recovered)
        }
    }

    private fun componentSnapshot(info: PackageInfo?, userId: Int, component: ComponentName): String =
        JSONObject(snapshot(info, userId)).put("component", component.flattenToString())
            .put("enabledSetting", ShizukuPackageInstallerUtils.componentEnabledSetting(component, userId)).toString()

    private fun verified(result: ShellResult, applied: Boolean): OperationResult =
        OperationResult(result.success && applied, if (!result.success) result.message
            else context.getString(if (applied) R.string.operation_success else R.string.operation_not_applied))

    suspend fun reinstall(
        packageName: String,
        userId: Int,
        batchId: String = UUID.randomUUID().toString(),
    ): OperationResult = recorded(packageName, userId, "reinstall", batchId) {
        val result = installExistingStep(packageName, userId)
        if (!result.success) OperationResult(false, result.message ?: context.getString(R.string.operation_failed))
        else {
            val installed = getPackageInfo(packageName, userId)?.applicationInfo?.let {
                it.flags and ApplicationInfo.FLAG_INSTALLED != 0
            } == true
            OperationResult(installed, context.getString(if (installed) R.string.operation_success else R.string.operation_not_applied))
        }
    }

    suspend fun removeUpdates(
        packageName: String,
        userId: Int,
        approvedDowngradeUsers: Set<Int> = emptySet(),
        batchId: String = UUID.randomUUID().toString(),
    ): OperationResult = recorded(packageName, userId, "remove_updates", batchId) { before ->
        val app = before?.applicationInfo ?: error(context.getString(R.string.operation_package_missing))
        val updated = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        if (!updated) return@recorded OperationResult(true, context.getString(R.string.no_leftover_updates), skipped = true)
        // Cleanup may temporarily install the package; never do this to essential packages.
        val protection = safety.inspect(listOf(packageName), userId).getValue(packageName)
        check(!protection.protected && protection.error == null) {
            protection.error ?: context.getString(R.string.safety_protected, packageName)
        }
        val impact = inspectUpdatesNow(packageName, userId)
        check(impact.otherInstalledProfiles.all { it.id in approvedDowngradeUsers }) {
            context.getString(R.string.update_profiles_changed)
        }
        val profilesBefore = ShizukuUserUtils.getUsers().associate { profile ->
            profile.id to isInstalled(packageName, profile.id)
        }
        val paths = (listOfNotNull(app.sourceDir) + app.splitSourceDirs.orEmpty()).distinct()
            .filter { it.startsWith("/data/app/") }.associateWith { File(it).length() }
        val result = UpdateCleanupSequence.execute(
            UpdateState(updated, impact.installed),
            readState = { getPackageInfo(packageName, userId)?.applicationInfo?.let {
                UpdateState(it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                    it.flags and ApplicationInfo.FLAG_INSTALLED != 0)
            } },
            reset = { uninstallStep(packageName, userId, 0) },
            installExisting = { installExistingStep(packageName, userId) },
            uninstallForUser = { uninstallStep(packageName, userId, 4) },
        )
        val preserved = profilesBefore.all { (id, installed) -> isInstalled(packageName, id) == installed }
        val success = result.success && preserved
        val freed = if (impact.otherInstalledProfiles.isEmpty()) paths.filterKeys { !File(it).exists() }.values.sum() else 0
        OperationResult(success,
            if (!preserved) context.getString(R.string.update_profile_state_changed)
            else result.message ?: context.getString(if (success) R.string.operation_success else R.string.operation_failed),
            freedBytes = freed)
    }

    private fun isInstalled(packageName: String, userId: Int): Boolean =
        getPackageInfo(packageName, userId)?.applicationInfo?.let { it.flags and ApplicationInfo.FLAG_INSTALLED != 0 } == true

    internal fun installExistingStep(packageName: String, userId: Int): PackageInstallerResult.Result {
        LogUtils.i("PackageOps", "install-existing $packageName user=$userId")
        return PackageInstallerResult.await(context) { sender ->
            HiddenApiBypass.invoke(IPackageInstaller::class.java,
                ShizukuPackageInstallerUtils.getPrivilegedPackageInstaller(),
                "installExistingPackage", packageName, 0x00400000,
                PackageManager.INSTALL_REASON_UNKNOWN, sender, userId, null)
        }
    }

    internal fun getPackageInfo(packageName: String, userId: Int): PackageInfo? =
        ShizukuPackageInstallerUtils.getPackageInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES, userId)

    internal fun uninstallStep(packageName: String, userId: Int, flags: Int): PackageInstallerResult.Result {
        LogUtils.i("PackageOps", "uninstall $packageName user=$userId flags=$flags")
        val installer = ShizukuPackageInstallerUtils.createPackageInstaller(
            ShizukuPackageInstallerUtils.getPrivilegedPackageInstaller(), "com.android.shell", userId, context,
        )
        val result = PackageInstallerResult.await(context) { sender ->
            HiddenApiBypass.invoke(PackageInstaller::class.java, installer, "uninstall", packageName, flags, sender)
        }
        if (!result.success) LogUtils.e("PackageOps", "uninstall failed: ${result.message}")
        return result
    }

    private suspend fun recorded(
        packageName: String,
        userId: Int,
        action: String,
        batchId: String,
        undoOf: String = "",
        snapshotter: (PackageInfo?, Int) -> String = { info, user -> snapshot(info, user) },
        operation: suspend (PackageInfo?) -> OperationResult,
    ): OperationResult = withContext(Dispatchers.IO) { mutationMutex.withLock {
        LogUtils.i("PackageOps", "$action $packageName user=$userId batch=$batchId")
        val id = UUID.randomUUID().toString()
        var before: PackageInfo? = null
        var previousState = "{}"
        var preflightError: Throwable? = null
        try {
            require(userId >= 0 && packageName.isNotBlank())
            if (action != "reinstall") check(packageName !in safety.alwaysProtected) {
                context.getString(R.string.safety_protected, packageName)
            }
            check(Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                context.getString(R.string.operation_shizuku_unavailable)
            }
            before = getPackageInfo(packageName, userId)
            previousState = snapshotter(before, userId)
        } catch (e: Exception) { preflightError = e }
        try {
            history.append(OperationRecord.newBuilder().setId(id).setBatchId(batchId)
                .setTimestampMs(System.currentTimeMillis()).setUserId(userId).setPackageName(packageName)
                .setAction(action).setPreviousState(previousState).setUndoOf(undoOf).build())
        } catch (e: Exception) {
            LogUtils.e("PackageOps", "Cannot persist operation before mutation", e)
            return@withContext OperationResult(false, context.getString(R.string.operation_history_failed))
        }
        // Once a mutation starts, finish verification/journaling even if the caller leaves the screen.
        withContext(NonCancellable) {
            val outcome = try {
                preflightError?.let { throw it }
                operation(before)
            } catch (e: Exception) {
                LogUtils.e("PackageOps", "$action failed for $packageName user=$userId", e.cause ?: e)
                OperationResult(false, (e.cause ?: e).message ?: context.getString(R.string.operation_failed))
            }
            val after = runCatching { snapshotter(getPackageInfo(packageName, userId), userId) }.getOrNull()
            val result = packageOutcome(previousState, after, outcome, context.getString(R.string.operation_state_unavailable))
            val changed = result.changed
            try {
                history.complete(id, result.success, result.message, after ?: "{}", changed, result.freedBytes, result.recoveryComplete)
            } catch (e: Exception) {
                LogUtils.e("PackageOps", "Cannot persist result; operation remains pending", e)
                return@withContext OperationResult(false, context.getString(R.string.operation_history_failed), changed)
            }
            if (result.success) LogUtils.i("PackageOps", "$action verified: $packageName user=$userId")
            else LogUtils.e("PackageOps", "$action failed: ${result.message}")
            result
        }
    } }

    private fun snapshot(info: PackageInfo?, userId: Int): String = JSONObject().apply {
        val app = info?.applicationInfo
        put("installed", app != null && app.flags and ApplicationInfo.FLAG_INSTALLED != 0)
        put("updatedSystemApp", app != null && app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0)
        put("enabled", app?.enabled ?: false)
        put("systemApp", app != null && app.flags and ApplicationInfo.FLAG_SYSTEM != 0)
        put("suspended", app != null && app.flags and ApplicationInfo.FLAG_SUSPENDED != 0)
        if (info != null) put("enabledSetting", ShizukuPackageInstallerUtils.applicationEnabledSetting(info.packageName, userId))
        put("sourceDir", app?.sourceDir.orEmpty())
        put("apkAvailable", app?.sourceDir?.let { File(it).isFile } == true)
        put("versionCode", info?.longVersionCode ?: 0)
    }.toString()
}
