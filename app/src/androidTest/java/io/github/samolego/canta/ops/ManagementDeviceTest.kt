package io.github.samolego.canta.ops

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.data.PresetStore
import io.github.samolego.canta.data.managementDataStore
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.LockdownSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import rikka.shizuku.Shizuku
import java.util.UUID

class ManagementDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val services get() = CanaServices.getInstance()
    private val pkg = "io.github.jordanwoodson.cana.fixture"
    @Before fun ready() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        assertEquals(PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission())
    }
    @Test fun mixedBatchUndoAndAllProfilePresetUseCapturedUsers() = runBlocking {
        val peer = services.privacy.snapshot(pkg, 0, PrivacyAction.METERED).toString()
        val batch = UUID.randomUUID().toString()
        assertTrue(services.packageOps.setEnabled(pkg, 10, false, batchId = batch).success)
        assertTrue(services.privacy.restrict(pkg, 10, PrivacyAction.METERED, batchId = batch).success)
        val captured = services.undo.lastBatch()
        assertEquals(listOf("metered", "disable"), captured.map { it.action })
        assertTrue(captured.all { it.userId == 10 && it.batchId == batch })
        val undo = services.undo.undo(captured)
        assertEquals(undo.toString(), 0, undo.failureCount)
        assertEquals(2, undo.successCount)
        assertEquals(peer, services.privacy.snapshot(pkg, 0, PrivacyAction.METERED).toString())
        assertTrue(services.packageOps.getPackageInfo(pkg, 10)!!.applicationInfo!!.enabled)
        assertEquals(2, services.undo.undo(captured).skippedCount)

        val store = PresetStore(context)
        val preset = CantaPresetData("All profile test", "", System.currentTimeMillis(), emptySet(), uuid = UUID.randomUUID().toString(),
            lockdown = listOf(LockdownSettings(pkg, denyMetered = true)), profileKind = "WORK")
        assertTrue(store.savePreset(preset))
        try {
            val saved = store.presetsFlow.first().single { it.uuid == preset.uuid }
            assertEquals("WORK", saved.profileKind)
            val applied = services.presets.apply(saved, listOf(0, 10), emptyMap())
            assertEquals(applied.toString(), 2, applied.successCount)
            assertEquals(0, applied.failureCount)
            for (user in listOf(0, 10)) assertEquals(1, services.privacy.snapshot(pkg, user, PrivacyAction.METERED).getInt("meteredPolicy") and 1)
            val records = services.history.records.first().filter { it.batchId == applied.batchId }.asReversed()
            assertEquals(0, services.undo.undo(records).failureCount)
            assertEquals(peer, services.privacy.snapshot(pkg, 0, PrivacyAction.METERED).toString())
        } finally { store.deletePreset(preset) }
    }
    @Test fun fingerprintChangeFindsRealReturnedWorkAppAndPostsNotification() = runBlocking {
        val originalState = services.management.state.first()
        val notifications = context.getSystemService(NotificationManager::class.java)
        // Grant before starting instrumentation and revoke after the suite: Android kills
        // the instrumented process if it revokes its own runtime notification permission.
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        var removal: io.github.samolego.canta.data.proto.OperationRecord? = null
        try {
            services.ota.check()
            val baseline = services.management.state.first()
            assertTrue(baseline.profilesList.any { it.userId == 10 })
            val result = services.packageOps.uninstall(pkg, 10, keepData = true)
            assertTrue(result.message, result.success)
            removal = services.history.records.first().last { it.packageName == pkg && it.userId == 10 && it.action == "uninstall_keep_data" }
            assertTrue(services.shell.exec(listOf("cmd", "package", "install-existing", "--user", "10", pkg)).success)
            context.managementDataStore.updateData { state ->
                state.toBuilder().clearProfiles().addAllProfiles(state.profilesList.map { profile ->
                    if (profile.userId != 10) profile else profile.toBuilder().setFingerprint("before-emulated-ota")
                        .clearSystemPackages().addAllSystemPackages(profile.systemPackagesList.filterNot { it == "com.android.printspooler" }).build()
                }).build()
            }
            services.ota.check()
            val pending = services.management.state.first().pendingList
            assertTrue(pending.any { it.packageName == pkg && it.userId == 10 && it.returned })
            assertTrue(pending.any { it.packageName == "com.android.printspooler" && it.userId == 10 && !it.returned })
            assertFalse(pending.any { it.packageName == pkg && it.userId == 0 })
            assertTrue(notifications.activeNotifications.any { it.id == 51740 })
            assertEquals(pending, services.management.state.first().pendingList)
        } finally {
            removal?.let { assertTrue(services.packageOps.undo(it).success) }
            context.managementDataStore.updateData { originalState }
            notifications.cancel(51740)
        }
    }
    @Test fun usageDataIsAvailableOnlyForTheCallingProfile() = runBlocking {
        services.selfGrants.grantMissing()
        assertNotNull(UsageRepository(context).lastUsed(0))
        assertNull(UsageRepository(context).lastUsed(10))
    }
}
