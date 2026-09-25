package io.github.samolego.canta.ops

import android.content.Context
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
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import java.util.UUID
import java.io.File

/** All mutations capture an explicit user and journal their before/after state. */
class PackageOps(context: Context, private val history: HistoryStore, private val safety: SafetyInspector) {
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
    ): OperationResult = recorded(packageName, userId, "uninstall", batchId) { before ->
        safety.requireAllowed(packageName, userId, approvedWarnings)
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
            resetFirst = resetToFactory && system && updated,
            uninstallFlags = if (system) 4 else 0,
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
            previousState = snapshot(before, userId)
        } catch (e: Exception) { preflightError = e }
        try {
            history.append(OperationRecord.newBuilder().setId(id).setBatchId(batchId)
                .setTimestampMs(System.currentTimeMillis()).setUserId(userId).setPackageName(packageName)
                .setAction(action).setPreviousState(previousState).build())
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
            val after = try { snapshot(getPackageInfo(packageName, userId), userId) } catch (_: Exception) { "{}" }
            val changed = previousState != "{}" && after != "{}" && previousState != after
            val result = outcome.copy(changed = changed)
            try {
                history.complete(id, result.success, result.message, after, changed, result.freedBytes)
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
        put("versionCode", info?.longVersionCode ?: 0)
    }.toString()
}
