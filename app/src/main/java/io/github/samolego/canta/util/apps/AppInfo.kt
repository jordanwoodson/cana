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
) : Parcelable {

    val name: String
        get() = appName ?: packageName.substringAfterLast('.')
    val removalInfo: RemovalRecommendation?
        get() = bloatData?.removal
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
                updateSizeBytes = updateApkSize(
                    packageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                    packageInfo.applicationInfo!!.sourceDir,
                    packageInfo.applicationInfo!!.splitSourceDirs?.toList().orEmpty(),
                ),
                isSuspended = packageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_SUSPENDED != 0,
            )
        }
    }
}
