package io.github.samolego.canta.util.apps

import java.io.File

internal fun updateApkSize(
    isUpdatedSystemApp: Boolean,
    sourceDir: String?,
    splitSourceDirs: List<String>,
    length: (String) -> Long = { File(it).length() },
): Long {
    if (!isUpdatedSystemApp || sourceDir?.startsWith("/data/app/") != true) return 0
    return (listOf(sourceDir) + splitSourceDirs).distinct().filter { it.startsWith("/data/app/") }
        .sumOf { path -> runCatching { length(path).coerceAtLeast(0) }.getOrDefault(0) }
}
