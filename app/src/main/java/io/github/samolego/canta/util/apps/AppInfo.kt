package io.github.samolego.canta.util.apps

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Parcelable
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.RemovalRecommendation
import kotlinx.parcelize.Parcelize

/**
 * Data class to hold information about an app.
 */
@Parcelize
data class AppInfo(
    private val appName: String?,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isUninstalled: Boolean,
    val isDisabled: Boolean,
    val bloatData: BloatData?,
    /** Only set for apps of other profiles, which Canta's own [PackageManager] can't look up. */
    val applicationInfo: ApplicationInfo? = null,
    val isUpdatedSystemApp: Boolean = false,
    val updateSizeBytes: Long = 0,
    val isSuspended: Boolean = false,
    val apkSizeBytes: Long = 0,
    val firstInstallTime: Long = 0,
    /** Null means unavailable for this profile; zero means no use in Android's retained history. */
    val lastUsed: Long? = null,
    val apkIdentity: String = "",
) : Parcelable {

    val name: String
        get() = appName ?: packageName.substringAfterLast('.')
    val removalInfo: RemovalRecommendation?
        get() = bloatData?.removal
    /** The same debloat-list rating is used by every profile, filter and action selection. */
    val shownRisk: RemovalRecommendation?
        get() = removalInfo
    val description: String?
        get() = bloatData?.description


    companion object {
        fun fromPackageInfo(
            packageInfo: PackageInfo,
            packageManager: PackageManager,
            isUninstalled: Boolean,
            bloatList: Map<String, BloatData> = emptyMap(),
            otherUser: Boolean = false,
        ): AppInfo {
            val bloatData = bloatList[packageInfo.packageName]
            val apk = ApkSnapshot.read(packageInfo.applicationInfo?.sourceDir,
                packageInfo.applicationInfo?.splitSourceDirs?.toList().orEmpty())

            val isSystemApp =
                (packageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val isDisabled = if (otherUser) {
                // Already holds the enabled state for the queried user
                !packageInfo.applicationInfo!!.enabled
            } else try {
                !packageManager.getApplicationInfo(packageInfo.packageName, 0).enabled
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }

            val versionName = packageInfo.versionName ?: "unknown"

            return AppInfo(
                appName = packageInfo.applicationInfo!!.loadLabel(packageManager).toString(),
                packageName = packageInfo.packageName,
                versionName = versionName,
                versionCode = packageInfo.longVersionCode,
                isSystemApp = isSystemApp,
                isUninstalled = isUninstalled,
                isDisabled = isDisabled,
                bloatData = bloatData,
                applicationInfo = if (otherUser) packageInfo.applicationInfo else null,
                isUpdatedSystemApp = packageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                updateSizeBytes = if (packageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 &&
                    apk?.files?.all { it.path.startsWith("/data/app/") } == true) apk.sizeBytes else 0,
                isSuspended = packageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_SUSPENDED != 0,
                apkSizeBytes = apk?.sizeBytes ?: 0,
                apkIdentity = apk?.identity.orEmpty(),
                firstInstallTime = packageInfo.firstInstallTime,
            )
        }
    }
}
