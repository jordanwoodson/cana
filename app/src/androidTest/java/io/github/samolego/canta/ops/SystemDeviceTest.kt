package io.github.samolego.canta.ops

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import rikka.shizuku.Shizuku
import java.io.File

class SystemDeviceTest {
    private val services get() = CanaServices.getInstance()
    private suspend fun ready() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
    }
    @Test fun allControlsRecordVerifyAndUndoExactPreviousValues() = runBlocking {
        ready()
        for (control in SystemControl.entries) {
            val before = services.system.snapshot(control)
            val values = when (control) {
                SystemControl.PRIVATE_DNS -> SystemPolicy.dns("hostname", "dns.quad9.net")
                SystemControl.CAPTIVE_PORTAL -> SystemPolicy.captivePortal("graphene")
                SystemControl.DATA_SAVER -> emptyMap()
                else -> mapOf(control.keys.single() to if (before.getJSONObject("settings").optString(control.keys.single()) == "1") "0" else "1")
            }
            val result = services.system.apply(control, values, if (control == SystemControl.DATA_SAVER) !before.getBoolean("dataSaver") else null)
            val record = services.history.records.first().last { it.action == "system" && it.undoOf.isEmpty() }
            try {
                assertTrue("$control: ${result.message}", result.success)
                assertTrue(record.completed && record.changed && record.success)
                assertTrue(SnapshotState.equal(before.toString(), record.previousState))
                assertEquals(control.name, JSONObject(record.afterState).getString("control"))
                val batch = services.undo.undo(listOf(record))
                assertEquals(batch.toString(), 1, batch.successCount)
                assertEquals(0, batch.failureCount)
                assertEquals(before.toString(), services.system.snapshot(control).toString())
                assertTrue(services.system.undo(record).skipped)
            } finally { services.system.undo(record) }
        }
    }
    @Test fun pcRecoveryRestoresNullableGlobalSettingsAndDataSaver() = runBlocking {
        ready()
        val controls = listOf(SystemControl.CAPTIVE_PORTAL, SystemControl.DATA_SAVER)
        val before = controls.associateWith { services.system.snapshot(it).toString() }
        val records = controls.map { control ->
            val result = if (control == SystemControl.CAPTIVE_PORTAL) services.system.apply(control, SystemPolicy.captivePortal("off"))
                else services.system.apply(control, dataSaver = !JSONObject(before[control]!!).getBoolean("dataSaver"))
            assertTrue(result.message, result.success)
            services.history.records.first().last { it.action == "system" && it.undoOf.isEmpty() }
        }
        try {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val file = File(instrumentation.targetContext.getExternalFilesDir(null), "cana-system-recovery.sh")
            file.writeText(RestoreScript.generate(records))
            val output = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("sh ${file.absolutePath}"))
                .bufferedReader().use { it.readText() }
            assertTrue(output, output.contains("0 failed commands; 0 manual steps"))
            controls.forEach { assertEquals(before[it], services.system.snapshot(it).toString()) }
        } finally { records.asReversed().forEach { services.system.undo(it) } }
    }
    @Test fun settingsWorkWithoutShizukuAndDataSaverReportsUnavailable() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("systemStage") == "unavailable")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        assertFalse(Shizuku.pingBinder())
        assertTrue(services.selfGrants.status().secureSettings)
        val control = SystemControl.WIFI_SCAN
        val before = services.system.snapshot(control)
        val value = if (before.getJSONObject("settings").optString(control.keys.single()) == "1") "0" else "1"
        val result = services.system.apply(control, mapOf(control.keys.single() to value))
        assertTrue(result.message, result.success)
        val record = services.history.records.first().last { it.action == "system" && it.undoOf.isEmpty() }
        assertTrue(services.system.undo(record).success)
        assertEquals(before.toString(), services.system.snapshot(control).toString())
        val unsupported = services.system.apply(SystemControl.DATA_SAVER, dataSaver = true)
        assertFalse(unsupported.success)
        assertFalse(unsupported.changed)
    }
}
