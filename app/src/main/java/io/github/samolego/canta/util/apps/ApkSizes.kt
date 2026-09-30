package io.github.samolego.canta.util.apps

import java.io.File

internal fun knownApkTotal(sizes: List<Long>): Long? = runCatching {
    sizes.fold(0L) { total, size ->
        require(size > 0) { "APK size is unavailable" }
        Math.addExact(total, size)
    }
}.getOrNull()

/** Active APK payload including splits. Zero means unavailable, never a partial total. */
internal fun apkSize(
    sourceDir: String?,
    splitSourceDirs: List<String>,
    length: (String) -> Long = { File(it).length() },
): Long {
    if (sourceDir.isNullOrBlank()) return 0
    return runCatching {
        (listOf(sourceDir) + splitSourceDirs).distinct().fold(0L) { total, path ->
            val bytes = length(path)
            require(bytes > 0) { "APK size is unavailable" }
            Math.addExact(total, bytes)
        }
    }.getOrDefault(0)
}

internal fun updateApkSize(
    isUpdatedSystemApp: Boolean,
    sourceDir: String?,
    splitSourceDirs: List<String>,
    length: (String) -> Long = { File(it).length() },
): Long {
    if (!isUpdatedSystemApp || sourceDir?.startsWith("/data/app/") != true) return 0
    if (splitSourceDirs.any { !it.startsWith("/data/app/") }) return 0
    return apkSize(sourceDir, splitSourceDirs, length)
}
