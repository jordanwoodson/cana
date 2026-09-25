package io.github.samolego.canta

import android.content.pm.ApplicationInfo
import android.content.pm.IPackageInstaller
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import io.github.samolego.canta.extension.getInfoForPackage
import io.github.samolego.canta.ui.CantaApp
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.PackageInstallerResult
import io.github.samolego.canta.util.UninstallSequence
import io.github.samolego.canta.util.apps.UserProfile
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils
import org.lsposed.hiddenapibypass.HiddenApiBypass

const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
const val APP_NAME = "Cana"
const val packageName = BuildConfig.APPLICATION_ID

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            enableEdgeToEdge()
        }
        super.onCreate(savedInstanceState)

        setContent {
            CantaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CantaApp(
                        uninstallApp = { packageName, userId, resetToFactory ->
                            uninstallApp(packageName, userId, resetToFactory)
                        },
                        canResetAppToFactory = { packageName, userId ->
                            checkIfCanResetToFactory(packageName, userId)
                        },
                        reinstallApp = { packageName, userId -> reinstallApp(packageName, userId) },
                        closeApp = { finishAndRemoveTask() },
                    )
                }
            }
        }
    }

    /**
     * Gets package info of an app installed for [userId], which may be another profile.
     */
    private fun getPackageInfoForUser(
        packageName: String,
        userId: Int,
        flags: Int = PackageManager.GET_META_DATA,
    ): PackageInfo? {
        if (userId == UserProfile.currentUserId) {
            return packageManager.getInfoForPackage(packageName, flags)
        }
        return try {
            ShizukuPackageInstallerUtils.getPackageInfo(packageName, flags, userId)
        } catch (e: Exception) {
            LogUtils.e(APP_NAME, "Failed to get package info of '$packageName' for user $userId", e)
            null
        }
    }

    /**
     * Checks if an app can be reset to factory version.
     * @param packageName package name of the app to check
     * @param userId user / profile the app is installed in
     * @return true if the app is a system app with updates
     */
    private fun checkIfCanResetToFactory(packageName: String, userId: Int): Boolean {
        val appInfo = getPackageInfoForUser(packageName, userId)?.applicationInfo ?: return false
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val hasUpdates = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return isSystem && hasUpdates
    }

    /**
     * Uninstalls app using Shizuku.
     * @param packageName package name of the app to uninstall
     * @param userId user / profile to uninstall the app from
     * @param resetToFactory whether to reset system app to factory version before uninstall
     */
    private fun uninstallApp(
        packageName: String,
        userId: Int,
        resetToFactory: Boolean = false,
    ): Boolean {
        val packageInfo = getPackageInfoForUser(packageName, userId) ?: return false
        val isSystem = (packageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val hasUpdates =
            (packageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        val shouldReset = resetToFactory && isSystem && hasUpdates
        LogUtils.i(
            APP_NAME,
            "Uninstalling '$packageName' for user $userId [system: $isSystem, hasUpdates: $hasUpdates, resetFirst: $shouldReset]"
        )
        val packageInstaller = getPackageInstaller(userId)

        // 0x00000004 = PackageManager.DELETE_SYSTEM_APP
        // 0x00000002 = PackageManager.DELETE_ALL_USERS
        // DELETE_ALL_USERS would remove the app from every profile, so only use it when
        // working on Canta's own profile, like upstream Canta does.
        val flags = when {
            isSystem -> 0x00000004
            userId == UserProfile.currentUserId -> 0x00000002
            else -> 0
        }
        val uninstall = { deleteFlags: Int ->
            LogUtils.i(APP_NAME, "Uninstall '$packageName' user $userId flags $deleteFlags")
            PackageInstallerResult.await(applicationContext) { intentSender ->
                HiddenApiBypass.invoke(
                    PackageInstaller::class.java,
                    packageInstaller,
                    "uninstall",
                    packageName,
                    deleteFlags,
                    intentSender
                )
            }
        }

        return try {
            val result = UninstallSequence.execute(
                resetFirst = shouldReset,
                uninstallFlags = flags,
                hasUpdates = {
                    getPackageInfoForUser(packageName, userId, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                        ?.applicationInfo?.let {
                            it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                        }
                },
                uninstall = uninstall,
            )
            if (!result.success) {
                LogUtils.e(
                    APP_NAME,
                    "Failed to uninstall '$packageName' for user $userId: ${result.message}"
                )
            }
            result.success
        } catch (e: Exception) {
            LogUtils.e(APP_NAME, "Failed to uninstall '$packageName' for user $userId")
            // HiddenApiBypass wraps the real error (e.g. SecurityException) in an InvocationTargetException
            LogUtils.e(APP_NAME, "Error: ${e.cause ?: e}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Reinstalls app using Shizuku. See <a
     * href="https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/pm/PackageManagerShellCommand.java;drc=bcb2b436bde55ee40050400783a9c083e77ce2fe;l=1408>PackageManagerShellCommand.java</a>
     * @param packageName package name of the app to reinstall (must preinstalled on the phone)
     * @param userId user / profile to reinstall the app for
     */
    private fun reinstallApp(packageName: String, userId: Int): Boolean {
        val installReason = PackageManager.INSTALL_REASON_UNKNOWN

        LogUtils.i(APP_NAME, "Reinstalling '$packageName' for user $userId")

        // PackageManager.INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS
        val installFlags = 0x00400000

        return try {
            val result = PackageInstallerResult.await(applicationContext) { intentSender ->
                HiddenApiBypass.invoke(
                    IPackageInstaller::class.java,
                    ShizukuPackageInstallerUtils.getPrivilegedPackageInstaller(),
                    "installExistingPackage",
                    packageName,
                    installFlags,
                    installReason,
                    intentSender,
                    userId,
                    null
                )
            }
            if (!result.success) {
                LogUtils.e(
                    APP_NAME,
                    "Failed to reinstall '$packageName' for user $userId: ${result.message}"
                )
            }
            result.success
        } catch (e: Exception) {
            LogUtils.e(APP_NAME, "Failed to reinstall '$packageName' for user $userId")
            // HiddenApiBypass wraps the real error (e.g. SecurityException) in an InvocationTargetException
            LogUtils.e(APP_NAME, "Error: ${e.cause ?: e}")
            e.printStackTrace()
            false
        }
    }

    private fun getPackageInstaller(userId: Int): PackageInstaller {
        val iPackageInstaller = ShizukuPackageInstallerUtils.getPrivilegedPackageInstaller()

        // The reason for use "com.android.shell" as installer package under adb is that
        // getMySessions will check installer package's owner
        return ShizukuPackageInstallerUtils.createPackageInstaller(
            iPackageInstaller,
            "com.android.shell",
            userId,
            this
        )
    }
}
