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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import java.util.UUID

/** All mutations capture an explicit user and journal their before/after state. */
class PackageOps(context: Context, private val history: HistoryStore) {
    private val context = context.applicationContext

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
    ): OperationResult = recorded(packageName, userId, "uninstall", batchId) { before ->
        val info = before?.applicationInfo ?: error(context.getString(R.string.operation_package_missing))
        check(info.flags and ApplicationInfo.FLAG_INSTALLED != 0) {
            context.getString(R.string.operation_already_uninstalled)
        }
        val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val updated = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
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
        val result = PackageInstallerResult.await(context) { sender ->
            HiddenApiBypass.invoke(
                IPackageInstaller::class.java,
                ShizukuPackageInstallerUtils.getPrivilegedPackageInstaller(),
                "installExistingPackage", packageName, 0x00400000,
                PackageManager.INSTALL_REASON_UNKNOWN, sender, userId, null,
            )
        }
        if (!result.success) OperationResult(false, result.message ?: context.getString(R.string.operation_failed))
        else {
            val installed = getPackageInfo(packageName, userId)?.applicationInfo?.let {
                it.flags and ApplicationInfo.FLAG_INSTALLED != 0
            } == true
            OperationResult(installed, context.getString(if (installed) R.string.operation_success else R.string.operation_not_applied))
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
        operation: (PackageInfo?) -> OperationResult,
    ): OperationResult = withContext(Dispatchers.IO) {
        LogUtils.i("PackageOps", "$action $packageName user=$userId batch=$batchId")
        val id = UUID.randomUUID().toString()
        var before: PackageInfo? = null
        var previousState = "{}"
        var preflightError: Throwable? = null
        try {
            require(userId >= 0 && packageName.isNotBlank())
            check(Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                context.getString(R.string.operation_shizuku_unavailable)
            }
            before = getPackageInfo(packageName, userId)
            previousState = snapshot(before)
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
            val after = try { snapshot(getPackageInfo(packageName, userId)) } catch (_: Exception) { "{}" }
            val changed = previousState != "{}" && after != "{}" && previousState != after
            val result = outcome.copy(changed = changed)
            try {
                history.complete(id, result.success, result.message, after, changed)
            } catch (e: Exception) {
                LogUtils.e("PackageOps", "Cannot persist result; operation remains pending", e)
                return@withContext OperationResult(false, context.getString(R.string.operation_history_failed), changed)
            }
            if (result.success) LogUtils.i("PackageOps", "$action verified: $packageName user=$userId")
            else LogUtils.e("PackageOps", "$action failed: ${result.message}")
            result
        }
    }

    private fun snapshot(info: PackageInfo?): String = JSONObject().apply {
        val app = info?.applicationInfo
        put("installed", app != null && app.flags and ApplicationInfo.FLAG_INSTALLED != 0)
        put("updatedSystemApp", app != null && app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0)
        put("enabled", app?.enabled ?: false)
        put("sourceDir", app?.sourceDir.orEmpty())
        put("versionCode", info?.longVersionCode ?: 0)
    }.toString()
}
