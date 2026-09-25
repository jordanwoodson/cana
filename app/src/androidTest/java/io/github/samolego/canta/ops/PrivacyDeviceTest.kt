package io.github.samolego.canta.ops

import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku
import java.io.File

@RunWith(AndroidJUnit4::class)
class PrivacyDeviceTest {
    private val services get() = CanaServices.getInstance()
    private val pkg = "io.github.jordanwoodson.cana.fixture"
    @Before fun emulatorOnly() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        assertEquals(PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission())
    }

    @Test fun typedPrivacyActionsAndUndoPreservePeerAndCapturedValues() = runBlocking {
        for ((user, peer) in listOf(0 to 10, 10 to 0)) for (action in PrivacyAction.entries) {
            val before = services.privacy.snapshot(pkg, user, action)
            val peerBefore = services.privacy.snapshot(pkg, peer, action)
            val result = services.privacy.restrict(pkg, user, action)
            if (action == PrivacyAction.BACKGROUND && before.getInt("standbyBucket") == 5) {
                assertFalse("Exempt standby must report partial failure", result.success)
                assertTrue(result.message, result.message.contains("exempts"))
                assertEquals("ignore", services.privacy.snapshot(pkg, user, action).getString("runAnyInBackground"))
            } else assertTrue("$action user=$user: ${result.message}", result.success)
            assertEquals(peerBefore.toString(), services.privacy.snapshot(pkg, peer, action).toString())
            val record = services.history.records.first().last { it.packageName == pkg && it.action == action.key && it.userId == user }
            assertTrue(record.completed)
            val undo = services.privacy.undo(record)
            assertTrue("Undo $action user=$user: ${undo.message}", undo.success)
            val after = services.privacy.snapshot(pkg, user, action)
            if (action == PrivacyAction.NETWORK) { before.remove("chainEnabled"); after.remove("chainEnabled") }
            assertEquals(before.toString(), after.toString())
            assertTrue(services.privacy.undo(record).skipped)
        }
        val refused = services.privacy.restrict("com.android.settings", 0, PrivacyAction.NETWORK)
        assertFalse(refused.success)
        assertFalse(refused.changed)
    }

    @Test fun desiredBlockReconcilesAndPcScriptRecoversWorkUid() = runBlocking {
        val result = services.privacy.restrict(pkg, 10, PrivacyAction.NETWORK)
        assertTrue(result.message, result.success)
        val original = services.history.records.first().last { it.packageName == pkg && it.action == "network" }
        val state = services.privacy.snapshot(pkg, 10, PrivacyAction.NETWORK)
        val appId = state.getInt("appId")
        val peerBefore = services.privacy.snapshot(pkg, 0, PrivacyAction.NETWORK).getInt("networkRule")
        val cleared = services.shell.exec(listOf("cana-privacy", "network-set", pkg, "10", "$appId", "1"))
        assertTrue(cleared.message, cleared.success)
        assertTrue(services.privacy.load(pkg, 10).desiredBlock)
        assertEquals(1, services.privacy.snapshot(pkg, 10, PrivacyAction.NETWORK).getInt("networkRule"))
        val reapplied = services.privacy.reapplyDesired()
        assertEquals(reapplied.toString(), 0, reapplied.failureCount)
        assertEquals(2, services.privacy.snapshot(pkg, 10, PrivacyAction.NETWORK).getInt("networkRule"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "cana-privacy-recovery.sh")
        file.writeText(RestoreScript.generate(listOf(original)))
        val output = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("sh ${file.absolutePath}"))
            .bufferedReader().use { it.readText() }
        assertTrue(output, output.contains("0 failed commands; 0 manual steps"))
        assertEquals(1, services.privacy.snapshot(pkg, 10, PrivacyAction.NETWORK).getInt("networkRule"))
        assertEquals(peerBefore, services.privacy.snapshot(pkg, 0, PrivacyAction.NETWORK).getInt("networkRule"))
        assertFalse(services.desiredPrivacy.blocks.first().any { it.packageName == pkg && it.userId == 10 })
        val undo = services.privacy.undo(original)
        assertTrue(undo.message, undo.success)
        assertFalse(services.desiredPrivacy.blocks.first().any { it.packageName == pkg && it.userId == 10 })
    }
    @Test fun meteredResetReconcilesAndPcRecoveryCancelsIntent() = runBlocking {
        val peer = services.privacy.snapshot(pkg, 0, PrivacyAction.METERED).toString()
        val result = services.privacy.restrict(pkg, 10, PrivacyAction.METERED)
        assertTrue(result.message, result.success)
        val original = services.history.records.first().last { it.packageName == pkg && it.action == "metered" && it.undoOf.isEmpty() }
        val state = services.privacy.snapshot(pkg, 10, PrivacyAction.METERED)
        assertTrue(state.getBoolean("desiredMetered"))
        val reset = services.shell.exec(listOf("cana-privacy", "metered-set", pkg, "10", state.getInt("appId").toString(), "0"))
        assertTrue(reset.message, reset.success)
        assertEquals(0, services.privacy.snapshot(pkg, 10, PrivacyAction.METERED).getInt("meteredPolicy"))
        assertEquals(0, services.privacy.reapplyDesired().failureCount)
        assertEquals(1, services.privacy.snapshot(pkg, 10, PrivacyAction.METERED).getInt("meteredPolicy") and 1)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "cana-metered-recovery.sh")
        file.writeText(RestoreScript.generate(listOf(original)))
        val output = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("sh ${file.absolutePath}"))
            .bufferedReader().use { it.readText() }
        assertTrue(output, output.contains("0 failed commands; 0 manual steps"))
        assertFalse(services.privacy.snapshot(pkg, 10, PrivacyAction.METERED).getBoolean("desiredMetered"))
        assertEquals(0, services.privacy.snapshot(pkg, 10, PrivacyAction.METERED).getInt("meteredPolicy"))
        assertEquals(peer, services.privacy.snapshot(pkg, 0, PrivacyAction.METERED).toString())
        assertTrue(services.privacy.undo(original).success)
    }
}
