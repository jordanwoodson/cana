package io.github.samolego.canta.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import io.github.samolego.canta.data.proto.*
import io.github.samolego.canta.ops.ManagementPolicy
import io.github.samolego.canta.ops.PackageInventory
import java.io.InputStream
import java.io.OutputStream

val Context.managementDataStore: DataStore<ManagementState> by dataStore("management.pb", ManagementSerializer)
object ManagementSerializer : Serializer<ManagementState> {
    override val defaultValue: ManagementState = ManagementState.getDefaultInstance()
    override suspend fun readFrom(input: InputStream): ManagementState = try { ManagementState.parseFrom(input) }
    catch (e: InvalidProtocolBufferException) { throw CorruptionException("Cannot read management state", e) }
    override suspend fun writeTo(t: ManagementState, output: OutputStream) = t.writeTo(output)
}

class ManagementStore(private val store: DataStore<ManagementState>) {
    val state = store.data
    suspend fun observe(fingerprint: String, inventories: List<PackageInventory>, history: List<OperationRecord>): ManagementState =
        store.updateData { before ->
            val profiles = before.profilesList.associateBy { it.userId }.toMutableMap()
            val pending = before.pendingList.associateBy { it.userId to it.packageName }.toMutableMap()
            for (inventory in inventories) {
                val previous = profiles[inventory.userId]
                if (previous != null) {
                    val changes = ManagementPolicy.otaChanges(previous.fingerprint, fingerprint,
                        listOf(PackageInventory(previous.userId, previous.installedList.toSet(), previous.systemPackagesList.toSet())),
                        listOf(inventory), history)
                    for (change in changes) pending[change.userId to change.packageName] = OtaPackageChange.newBuilder()
                        .setUserId(change.userId).setPackageName(change.packageName).setReturned(change.returned).setFingerprint(fingerprint).build()
                }
                pending.entries.removeAll { (key, _) -> key.first == inventory.userId && key.second !in inventory.installed }
                profiles[inventory.userId] = ProfileInventory.newBuilder().setUserId(inventory.userId).setFingerprint(fingerprint)
                    .addAllInstalled(inventory.installed.sorted()).addAllSystemPackages(inventory.system.sorted()).build()
            }
            before.toBuilder().clearProfiles().addAllProfiles(profiles.values.sortedBy { it.userId })
                .clearPending().addAllPending(pending.values.sortedWith(compareBy({ it.userId }, { it.packageName }))).build()
        }
    suspend fun dismiss(changes: List<OtaPackageChange>) {
        store.updateData { it.toBuilder().clearPending().addAllPending(it.pendingList.filterNot { change -> change in changes }).build() }
    }
    suspend fun notified(signature: String) { store.updateData { it.toBuilder().setNotifiedChanges(signature).build() } }
}
