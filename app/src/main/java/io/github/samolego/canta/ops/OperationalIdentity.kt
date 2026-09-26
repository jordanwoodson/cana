package io.github.samolego.canta.ops

import android.content.Context
import java.io.File
import java.util.UUID

/** This installation token intentionally cannot follow an Android backup or device transfer. */
object OperationalIdentity {
    @Synchronized fun current(context: Context): String {
        val file = File(context.noBackupFilesDir, "operational-installation-id")
        if (file.isFile) return file.readText().trim().also { require(it.isNotEmpty()) }
        val identity = UUID.randomUUID().toString()
        file.parentFile?.mkdirs()
        file.writeText(identity)
        return identity
    }
}
