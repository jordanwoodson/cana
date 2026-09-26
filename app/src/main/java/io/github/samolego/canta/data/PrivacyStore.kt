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
    val state = store.data
    suspend fun bindInstallation(installationId: String) {
        require(installationId.isNotBlank())
        store.updateData { current ->
            if (current.installationId == installationId) current else {
                fun quarantine(block: NetworkBlock) = block.toBuilder().clearIdentity().clearSafetyConditions()
                    .clearApprovals().setStatus("needs_review").setStatusMessage("").setCheckedAtMs(0).build()
                current.toBuilder().setInstallationId(installationId)
                    .clearNetworkBlocks().addAllNetworkBlocks(current.networkBlocksList.map(::quarantine))
                    .clearMeteredBlocks().addAllMeteredBlocks(current.meteredBlocksList.map(::quarantine)).build()
            }
        }
    }
    suspend fun saveReviewed(block: NetworkBlock, metered: Boolean) {
        store.updateData { current ->
            val entries = if (metered) current.meteredBlocksList else current.networkBlocksList
            val updated = entries.filterNot { it.packageName == block.packageName && it.userId == block.userId } + block
            current.toBuilder().apply {
                if (metered) clearMeteredBlocks().addAllMeteredBlocks(updated)
                else clearNetworkBlocks().addAllNetworkBlocks(updated)
            }.build()
        }
    }
    suspend fun status(packageName: String, userId: Int, metered: Boolean, status: String, message: String = "") {
        require(status in setOf("pending", "verified", "needs_review", "failed", "disconnected"))
        store.updateData { current ->
            val entries = if (metered) current.meteredBlocksList else current.networkBlocksList
            val updated = entries.map { if (it.packageName == packageName && it.userId == userId)
                it.toBuilder().setStatus(status).setStatusMessage(message.take(4096)).setCheckedAtMs(System.currentTimeMillis()).build() else it }
            current.toBuilder().apply {
                if (metered) clearMeteredBlocks().addAllMeteredBlocks(updated)
                else clearNetworkBlocks().addAllNetworkBlocks(updated)
            }.build()
        }
    }
    suspend fun forget(packageName: String, userId: Int, metered: Boolean) {
        if (metered) setMetered(packageName, userId, 0, false) else setBlock(packageName, userId, 0, false)
    }
    suspend fun restoreIntent(packageName: String, userId: Int, appId: Int, blocked: Boolean, metered: Boolean,
        prior: NetworkBlock? = null) {
        if (!blocked) { forget(packageName, userId, metered); return }
        require(prior == null || prior.packageName == packageName && prior.userId == userId && prior.appId == appId) {
            "Saved consent belongs to another package or profile"
        }
        val restored = prior ?: NetworkBlock.newBuilder().setPackageName(packageName).setUserId(userId).setAppId(appId).build()
        saveReviewed(restored.toBuilder().setStatus(if (restored.identity.isEmpty()) "needs_review" else "pending")
            .clearStatusMessage().setCheckedAtMs(0).build(), metered)
    }
    val blocks = store.data.map { it.networkBlocksList.toList() }
    val meteredBlocks = store.data.map { it.meteredBlocksList.toList() }
    suspend fun setMetered(packageName: String, userId: Int, appId: Int, blocked: Boolean) {
        store.updateData { current ->
            current.toBuilder().clearMeteredBlocks().addAllMeteredBlocks(current.meteredBlocksList.filterNot {
                it.packageName == packageName && it.userId == userId
            }).apply {
                if (blocked) addMeteredBlocks(current.meteredBlocksList.firstOrNull {
                    it.packageName == packageName && it.userId == userId && it.appId == appId
                } ?: NetworkBlock.newBuilder().setPackageName(packageName)
                    .setUserId(userId).setAppId(appId).setStatus("needs_review").build())
            }.build()
        }
    }
    suspend fun setBlock(packageName: String, userId: Int, appId: Int, blocked: Boolean) {
        store.updateData { current ->
            current.toBuilder().clearNetworkBlocks().addAllNetworkBlocks(current.networkBlocksList.filterNot {
                it.packageName == packageName && it.userId == userId
            }).apply {
                if (blocked) addNetworkBlocks(current.networkBlocksList.firstOrNull {
                    it.packageName == packageName && it.userId == userId && it.appId == appId
                } ?: NetworkBlock.newBuilder().setPackageName(packageName)
                    .setUserId(userId).setAppId(appId).setStatus("needs_review").build())
            }.build()
        }
    }
}
