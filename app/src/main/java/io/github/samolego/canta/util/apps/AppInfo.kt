package io.github.samolego.canta.util.apps

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Parcelable
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.RemovalRecommendation
import kotlinx.coroutines.CancellationException
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
    /** Retained from the selected profile, including packages removed from that profile. */
    val applicationInfo: ApplicationInfo? = null,
    val isUpdatedSystemApp: Boolean = false,
    val updateSizeBytes: Long = 0,
    val isSuspended: Boolean = false,
    val apkSizeBytes: Long = 0,
    val firstInstallTime: Long = 0,
    /** Null means unavailable for this profile; zero means no use in Android's retained history. */
    val lastUsed: Long? = null,
    val apkIdentity: String = "",
    val metadataIncomplete: Boolean = false,
    val appTypeKnown: Boolean = true,
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
            val info = packageInfo.applicationInfo
            var incomplete = info == null
            fun <T> readMetadata(field: String, fallback: T, read: () -> T): T = try {
                read()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                incomplete = true
                LogUtils.w("AppInfo", "Cannot read ${packageInfo.packageName} $field: ${e.message}")
                fallback
            }
            val apk = ApkSnapshot.read(info?.sourceDir, info?.splitSourceDirs?.toList().orEmpty())
            val flags = info?.flags ?: 0
            val label = if (info == null) packageInfo.packageName else
                readMetadata("label", packageInfo.packageName) {
                    info.loadLabel(packageManager).toString().ifBlank { packageInfo.packageName }
                }

            val isSystemApp = (flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val isDisabled = if (info == null) false else if (otherUser || isUninstalled) {
                // Use the queried profile's state; a name lookup excludes removed packages.
                !info.enabled
            } else readMetadata("enabled state", !info.enabled) {
                !packageManager.getApplicationInfo(packageInfo.packageName, 0).enabled
            }

            val versionName = packageInfo.versionName ?: "unknown"

            return AppInfo(
                appName = label,
                packageName = packageInfo.packageName,
                versionName = versionName,
                versionCode = packageInfo.longVersionCode,
                isSystemApp = isSystemApp,
                isUninstalled = isUninstalled,
                isDisabled = isDisabled,
                bloatData = bloatData,
                applicationInfo = info,
                isUpdatedSystemApp = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                updateSizeBytes = if (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 &&
                    apk?.files?.all { it.path.startsWith("/data/app/") } == true) apk.sizeBytes else 0,
                isSuspended = flags and ApplicationInfo.FLAG_SUSPENDED != 0,
                apkSizeBytes = apk?.sizeBytes ?: 0,
                apkIdentity = apk?.identity.orEmpty(),
                firstInstallTime = packageInfo.firstInstallTime,
                metadataIncomplete = incomplete,
                appTypeKnown = info != null,
            )
        }
    }
}
