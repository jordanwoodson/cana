package io.github.samolego.canta.util.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.ComponentName
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass
import io.github.samolego.canta.util.HiddenApiAccess
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException

/**
 * Taken from <a href="https://github.com/depau/fdroid_shizuku_privileged_extension/blob/main/app/src/main/java/org/fdroid/fdroid/privileged/ShizukuPackageInstallerUtils.kt">FDroid Priv</a>.
 */
object ShizukuPackageInstallerUtils {
    private val PACKAGE_MANAGER: IPackageManager by lazy {
        HiddenApiAccess.ensureReady()

        IPackageManager.Stub.asInterface(
            ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService(
                    "package"
                )
            )
        )
    }

    /** Android 9 returns the install status synchronously and takes an explicit target user. */
    fun installExistingOnPie(packageName: String, userId: Int): Int =
        HiddenApiBypass.invoke(IPackageManager::class.java, PACKAGE_MANAGER, "installExistingPackageAsUser",
            packageName, userId, 0, PackageManager.INSTALL_REASON_UNKNOWN) as Int

    fun getPrivilegedPackageInstaller(): IPackageInstaller {
        val packageInstaller: IPackageInstaller = PACKAGE_MANAGER.packageInstaller
        return IPackageInstaller.Stub.asInterface(ShizukuBinderWrapper(packageInstaller.asBinder()))
    }

    fun installSource(packageName: String, userId: Int): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        val source = HiddenApiBypass.invoke(IPackageManager::class.java, PACKAGE_MANAGER, "getInstallSourceInfo",
            packageName, userId) as? android.content.pm.InstallSourceInfo
        return source?.installingPackageName
    }

    fun activityHandlers(intent: Intent, userId: Int): Set<String> {
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
        val platformFlags: Any = if (Build.VERSION.SDK_INT >= 33) flags.toLong() else flags
        val slice = checkNotNull(HiddenApiBypass.invoke(IPackageManager::class.java, PACKAGE_MANAGER,
            "queryIntentActivities", intent, intent.type, platformFlags, userId))
        @Suppress("UNCHECKED_CAST")
        val matches = slice.javaClass.getMethod("getList").invoke(slice) as List<ResolveInfo>
        return matches.map { it.activityInfo.packageName }.toSet()
    }

    fun permissionControllerPackage(): String? = HiddenApiBypass.invoke(
        IPackageManager::class.java, PACKAGE_MANAGER, "getPermissionControllerPackageName",
    ) as String?

    fun applicationEnabledSetting(packageName: String, userId: Int): Int = HiddenApiBypass.invoke(
        IPackageManager::class.java, PACKAGE_MANAGER, "getApplicationEnabledSetting", packageName, userId,
    ) as Int

    fun componentEnabledSetting(component: ComponentName, userId: Int): Int = HiddenApiBypass.invoke(
        IPackageManager::class.java, PACKAGE_MANAGER, "getComponentEnabledSetting", component, userId,
    ) as Int

    /**
     * Same as [PackageManager.getInstalledPackages], but for any user / profile.
     */
    fun getInstalledPackages(flags: Int, userId: Int): List<PackageInfo> {
        // flags became a long in Android 13
        val slice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            HiddenApiBypass.invoke(
                IPackageManager::class.java,
                PACKAGE_MANAGER,
                "getInstalledPackages",
                flags.toLong(),
                userId
            )
        } else {
            HiddenApiBypass.invoke(
                IPackageManager::class.java,
                PACKAGE_MANAGER,
                "getInstalledPackages",
                flags,
                userId
            )
        } ?: return emptyList()

        // ParceledListSlice<PackageInfo>
        @Suppress("UNCHECKED_CAST")
        return slice.javaClass.getMethod("getList").invoke(slice) as List<PackageInfo>
    }

    /**
     * Same as [PackageManager.getPackageInfo], but for any user / profile.
     * @return null if the package isn't installed for [userId]
     */
    fun getPackageInfo(packageName: String, flags: Int, userId: Int): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            HiddenApiBypass.invoke(
                IPackageManager::class.java,
                PACKAGE_MANAGER,
                "getPackageInfo",
                packageName,
                flags.toLong(),
                userId
            )
        } else {
            HiddenApiBypass.invoke(
                IPackageManager::class.java,
                PACKAGE_MANAGER,
                "getPackageInfo",
                packageName,
                flags,
                userId
            )
        } as PackageInfo?
    }

    /**
     * Taken from https://github.com/RikkaApps/Shizuku-API/blob/01e08879d58a5cb11a333535c6ddce9f7b7c88ff/demo/src/main/java/rikka/shizuku/demo/util/PackageInstallerUtils.java#L15
     * @author RikkaW
     */
    @Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        InstantiationException::class,
    )
    fun createPackageInstaller(
        installer: IPackageInstaller?,
        installerPackageName: String?,
        userId: Int,
        context: Context,
    ): PackageInstaller {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            return PackageInstaller::class.java.getConstructor(
                IPackageInstaller::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            ).newInstance(installer, installerPackageName, null, userId)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PackageInstaller::class.java.getConstructor(
                IPackageInstaller::class.java, String::class.java, Int::class.java
            )
                .newInstance(installer, installerPackageName, userId)
        } else {
            return PackageInstaller::class.java.getConstructor(
                Context::class.java,
                PackageManager::class.java,
                IPackageInstaller::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
                .newInstance(
                    context,
                    context.packageManager,
                    installer,
                    installerPackageName,
                    userId
                )
        }
    }
}
