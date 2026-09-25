package io.github.samolego.canta.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import io.github.samolego.canta.data.proto.NetworkBlock
import io.github.samolego.canta.data.proto.PrivacyDesiredState
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream

val Context.privacyDataStore: DataStore<PrivacyDesiredState> by dataStore("privacy_desired.pb", PrivacySerializer)
object PrivacySerializer : Serializer<PrivacyDesiredState> {
    override val defaultValue = PrivacyDesiredState.getDefaultInstance()
    override suspend fun readFrom(input: InputStream): PrivacyDesiredState = try { PrivacyDesiredState.parseFrom(input) }
    catch (e: InvalidProtocolBufferException) { throw CorruptionException("Cannot read desired privacy state", e) }
    override suspend fun writeTo(t: PrivacyDesiredState, output: OutputStream) = t.writeTo(output)
}
class PrivacyStore(private val store: DataStore<PrivacyDesiredState>) {
    val blocks = store.data.map { it.networkBlocksList.toList() }
    val meteredBlocks = store.data.map { it.meteredBlocksList.toList() }
    suspend fun setMetered(packageName: String, userId: Int, appId: Int, blocked: Boolean) {
        store.updateData { current ->
            current.toBuilder().clearMeteredBlocks().addAllMeteredBlocks(current.meteredBlocksList.filterNot {
                it.packageName == packageName && it.userId == userId
            }).apply {
                if (blocked) addMeteredBlocks(NetworkBlock.newBuilder().setPackageName(packageName)
                    .setUserId(userId).setAppId(appId).build())
            }.build()
        }
    }
    suspend fun setBlock(packageName: String, userId: Int, appId: Int, blocked: Boolean) {
        store.updateData { current ->
            current.toBuilder().clearNetworkBlocks().addAllNetworkBlocks(current.networkBlocksList.filterNot {
                it.packageName == packageName && it.userId == userId
            }).apply {
                if (blocked) addNetworkBlocks(NetworkBlock.newBuilder().setPackageName(packageName)
                    .setUserId(userId).setAppId(appId).build())
            }.build()
        }
    }
}
