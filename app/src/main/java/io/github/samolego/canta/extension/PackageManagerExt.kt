package io.github.samolego.canta.extension

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.apps.AppInfo
import io.github.samolego.canta.util.apps.UserProfile
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils


private fun PackageManager.getUninstalledPackages(installedPackages: List<PackageInfo>): List<PackageInfo> {
    val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES

    // Get uninstalled packages + installed packages
    val uninstalledPackages = getPackages(flags).toSet()

    val installed = installedPackages.map { it.packageName }
    val minus = uninstalledPackages.filter { !installed.contains(it.packageName) }

    // Return only apps that have been uninstalled
    return minus.toList()
}

fun PackageManager.getAllPackagesInfo(): List<AppInfo> {
    val installedPackages = getInstalledPackages()
    val uninstalledPackages = getUninstalledPackages(installedPackages)

    val all = uninstalledPackages.map { app ->
        AppInfo.fromPackageInfo(app, this, true)
    } + installedPackages.map { app ->
        AppInfo.fromPackageInfo(app, this, false)
    }

    return all
}

/**
 * Like [getAllPackagesInfo], but for any user / profile. Other profiles can't be queried by Canta
 * itself, so those go through Shizuku.
 */
fun PackageManager.getAllPackagesInfo(userId: Int): List<AppInfo> {
    if (userId == UserProfile.currentUserId) {
        return getAllPackagesInfo()
    }

    val installedPackages =
        ShizukuPackageInstallerUtils.getInstalledPackages(PackageManager.GET_META_DATA, userId)
    val installed = installedPackages.mapTo(HashSet()) { it.packageName }
    val uninstalledPackages = ShizukuPackageInstallerUtils
        .getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES, userId)
        .filter { it.packageName !in installed }

    return uninstalledPackages.map { app ->
        AppInfo.fromPackageInfo(app, this, true, otherUser = true)
    } + installedPackages.map { app ->
        AppInfo.fromPackageInfo(app, this, false, otherUser = true)
    }
}

fun PackageManager.getInstalledPackages(): List<PackageInfo> {
    val flags = PackageManager.GET_META_DATA
    return getPackages(flags)
}

private fun PackageManager.getPackages(flags: Int): List<PackageInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.getInstalledPackages(
            PackageManager.PackageInfoFlags.of(flags.toLong())
        )
    } else {
        this.getInstalledPackages(flags)
    }
}

fun PackageManager.getInfoForPackage(
    packageName: String,
    flags: Int = PackageManager.GET_META_DATA,
): PackageInfo? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            this.getPackageInfo(
                packageName,
                flags
            )
        }
    } catch (e: NameNotFoundException) {
        LogUtils.e("PackageManagerExt", "Failed to get package info", e)
        null
    }
}
