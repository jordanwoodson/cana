package io.github.samolego.canta.util.apps

import java.io.File
import java.security.MessageDigest

/** File metadata for cache freshness, captured off the UI thread; not a content integrity check. */
data class ApkSnapshot(val files: List<File>, val sizeBytes: Long, val identity: String) {
    companion object {
        fun read(sourceDir: String?, splitSourceDirs: List<String>): ApkSnapshot? {
            if (sourceDir.isNullOrBlank()) return null
            return runCatching {
                val files = (listOf(sourceDir) + splitSourceDirs).distinct().sorted().map(::File)
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                for (file in files) {
                    require(file.isFile && file.canRead()) { "APK unavailable" }
                    val bytes = file.length()
                    require(bytes > 0)
                    size = Math.addExact(size, bytes)
                    digest.update("${file.absolutePath}\u0000$bytes\u0000${file.lastModified()}\u0000".toByteArray(Charsets.UTF_8))
                }
                ApkSnapshot(files, size, digest.digest().joinToString("") { "%02x".format(it) })
            }.getOrNull()
        }
    }
}
