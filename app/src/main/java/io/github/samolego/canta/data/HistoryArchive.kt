package io.github.samolego.canta.data

import io.github.samolego.canta.data.proto.OperationHistory
import io.github.samolego.canta.data.proto.OperationRecord
import java.io.File
import java.security.MessageDigest

/** Cold recovery records are separate from the frequently rewritten active DataStore. */
class HistoryArchive(private val directory: File) {
    @Synchronized fun append(records: List<OperationRecord>) {
        if (records.isEmpty()) return
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create history archive" }
        val key = MessageDigest.getInstance("SHA-256").digest(records.joinToString("\n") { it.id }.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val target = File(directory, "$key.pb")
        if (target.isFile) return // A DataStore retry must not duplicate an archive segment.
        val temporary = File.createTempFile("history-", ".tmp", directory)
        try {
            temporary.outputStream().use { stream ->
                OperationHistory.newBuilder().addAllRecords(records).build().writeTo(stream)
                stream.fd.sync()
            }
            check(temporary.renameTo(target)) { "Cannot persist history archive" }
        } finally { temporary.delete() }
    }
    @Synchronized fun read(): List<OperationRecord> = directory.listFiles().orEmpty()
        .filter { it.extension == "pb" }.flatMap { file -> file.inputStream().use { OperationHistory.parseFrom(it).recordsList } }
        .distinctBy { it.id }.sortedBy { it.sequenceNumber }
}
