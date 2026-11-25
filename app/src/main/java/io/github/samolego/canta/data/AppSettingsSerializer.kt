package io.github.samolego.canta.data

import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import io.github.samolego.canta.data.proto.AppSettings
import androidx.datastore.core.CorruptionException
import java.io.InputStream
import java.io.OutputStream

object AppSettingsSerializer : Serializer<AppSettings> {
    override val defaultValue: AppSettings = AppSettings
        .newBuilder()
        .setAutoUpdateBloatList(true)
        .setConfirmBeforeUninstall(true)
        .setListSourceMigrated(true)
        .build()

    override suspend fun readFrom(input: InputStream): AppSettings {
        val data = input.buffered()
        data.mark(1)
        if (data.read() == -1) throw CorruptionException("Empty settings file")
        data.reset()
        return try { AppSettings.parseFrom(data) }
        catch (e: InvalidProtocolBufferException) { throw CorruptionException("Unreadable settings file", e) }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) = t.writeTo(output)
}
