package io.github.samolego.canta.ops

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File

/** Run prepare, reboot emulator, start Shizuku, then verifyAndRestore as separate invocations. */
class PrivacyRebootDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val services get() = CanaServices.getInstance()
    private val pkg = "io.github.jordanwoodson.cana.fixture"
    private val stateFile get() = File(context.filesDir, "privacy-reboot-test.json")
    private suspend fun ready() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
    }
    @Test fun prepare() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rebootStage") == "prepare")
        ready()
        val state = JSONObject()
        state.put("peerMetered", services.privacy.snapshot(pkg, 0, PrivacyAction.METERED).toString())
        state.put("peerRule", services.privacy.snapshot(pkg, 0, PrivacyAction.NETWORK).getInt("networkRule"))
        for (action in listOf(PrivacyAction.METERED, PrivacyAction.NETWORK)) {
            val result = services.privacy.restrict(pkg, 10, action)
            assertTrue(result.message, result.success)
            state.put(action.key, services.history.records.first().last { it.packageName == pkg && it.action == action.key }.id)
        }
        stateFile.writeText(state.toString())
        assertTrue(services.desiredPrivacy.blocks.first().any { it.packageName == pkg && it.userId == 10 })
    }
    @Test fun verifyAndRestore() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rebootStage") == "verify")
        ready()
        val state = JSONObject(stateFile.readText())
        assertTrue(services.desiredPrivacy.blocks.first().any { it.packageName == pkg && it.userId == 10 })
        withTimeout(120_000) {
            while (services.privacy.snapshot(pkg, 10, PrivacyAction.NETWORK).let { !it.getBoolean("chainEnabled") || it.getInt("networkRule") != 2 } ||
                services.privacy.snapshot(pkg, 10, PrivacyAction.METERED).getInt("meteredPolicy") and 1 == 0) delay(2_000)
        }
        assertEquals(state.getInt("peerRule"), services.privacy.snapshot(pkg, 0, PrivacyAction.NETWORK).getInt("networkRule"))
        assertEquals(state.getString("peerMetered"), services.privacy.snapshot(pkg, 0, PrivacyAction.METERED).toString())
        for (action in listOf(PrivacyAction.NETWORK, PrivacyAction.METERED)) {
            val record = services.history.records.first().single { it.id == state.getString(action.key) }
            val result = services.privacy.undo(record)
            assertTrue(result.message, result.success)
        }
        assertFalse(services.desiredPrivacy.blocks.first().any { it.packageName == pkg && it.userId == 10 })
        stateFile.delete()
        Unit
    }
    @Test fun cleanupInterruptedRebootTest() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rebootStage") == "cleanup")
        ready()
        val state = JSONObject(stateFile.readText())
        for (action in listOf(PrivacyAction.NETWORK, PrivacyAction.METERED)) {
            val record = services.history.records.first().single { it.id == state.getString(action.key) }
            val result = services.privacy.undo(record)
            assertTrue(result.message, result.success)
        }
        stateFile.delete()
        Unit
    }
    @Test fun desiredStateRemainsVisibleBeforeBinderArrives() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rebootStage") == "unavailable")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        assertFalse(Shizuku.pingBinder())
        val state = services.privacy.load(pkg, 10)
        assertTrue(state.desiredBlock)
        assertFalse(state.connected)
        assertFalse(state.values.containsKey(PrivacyAction.NETWORK))
    }
    @Test fun recoveryEndpointRejectsUnprivilegedCallers() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        try {
            context.contentResolver.call(Uri.parse("content://io.github.jordanwoodson.cana.recovery"), "network-desired", null,
                Bundle().apply { putString("package", pkg); putInt("user", 10); putInt("appId", 10153); putBoolean("blocked", false) })
            fail("An ordinary app must not be able to invoke shell recovery")
        } catch (_: SecurityException) { }
    }
}
