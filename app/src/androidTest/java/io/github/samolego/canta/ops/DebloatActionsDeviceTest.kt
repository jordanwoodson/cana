package io.github.samolego.canta.ops

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils as Packages
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import rikka.shizuku.Shizuku

class DebloatActionsDeviceTest {
    @Test fun actionsAndExactUndoPreserveOtherProfileAndKeptData() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        val services = CanaServices.getInstance()
        val ops = services.packageOps
        val name = "io.github.jordanwoodson.cana.fixture"
        val component = ComponentName(name, "com.google.android.gms.measurement.FixtureService")
        suspend fun undoLast() {
            val record = services.history.records.first().last()
            assertTrue(record.completed && record.success && record.changed)
            val undo = ops.undo(record)
            assertTrue(undo.message, undo.success)
            assertEquals(record.id, services.history.records.first().last().undoOf)
            assertTrue(ops.undo(record).skipped)
        }
        fun adb(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
        for ((selected, other) in listOf(0 to 10, 10 to 0)) {
            assertTrue(ops.reinstall(name, selected).success)
            assertTrue(ops.reinstall(name, other).success)
            val before = Packages.applicationEnabledSetting(name, selected)
            val peer = Packages.applicationEnabledSetting(name, other)
            val disabled = ops.setEnabled(name, selected, false)
            assertTrue(disabled.message, disabled.success)
            assertEquals(3, Packages.applicationEnabledSetting(name, selected))
            assertEquals(peer, Packages.applicationEnabledSetting(name, other))
            undoLast()
            assertEquals(before, Packages.applicationEnabledSetting(name, selected))
            val suspended = ops.setSuspended(name, selected, true)
            assertTrue(suspended.message, suspended.success)
            assertEquals(0, Packages.getPackageInfo(name, 0, other)!!.applicationInfo!!.flags and ApplicationInfo.FLAG_SUSPENDED)
            undoLast()
            assertEquals(0, Packages.getPackageInfo(name, 0, selected)!!.applicationInfo!!.flags and ApplicationInfo.FLAG_SUSPENDED)
            val catalog = services.components.load(name, selected)
            assertTrue(catalog.editable)
            assertTrue(catalog.components.map { it.kind }.containsAll(listOf("activity", "service", "receiver", "provider")))
            assertTrue(catalog.components.first { it.name == component }.trackers.isNotEmpty())
            val componentBefore = Packages.componentEnabledSetting(component, selected)
            val componentPeer = Packages.componentEnabledSetting(component, other)
            val blocked = ops.setComponentEnabled(component, selected, false)
            assertTrue(blocked.message, blocked.success)
            assertEquals(componentPeer, Packages.componentEnabledSetting(component, other))
            undoLast()
            assertEquals(componentBefore, Packages.componentEnabledSetting(component, selected))
            adb("run-as $name --user $selected mkdir -p files")
            adb("run-as $name --user $selected touch files/cana-retained")
            assertTrue(adb("run-as $name --user $selected ls files").contains("cana-retained"))
            val kept = ops.uninstall(name, selected, keepData = true)
            assertTrue(kept.message, kept.success)
            assertNotEquals(0, Packages.getPackageInfo(name, 0, other)!!.applicationInfo!!.flags and ApplicationInfo.FLAG_INSTALLED)
            undoLast()
            assertTrue(adb("run-as $name --user $selected ls files").contains("cana-retained"))
        }
        val regular = services.components.load("com.android.printspooler", 0)
        assertFalse(regular.editable)
        val refusal = ops.setComponentEnabled(regular.components.first().name, 0, false)
        assertFalse(refusal.success)
        assertFalse(refusal.changed)
        assertTrue(refusal.message.contains("Root-backed"))
    }
}
