package io.github.samolego.canta.ops

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils as Packages
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import rikka.shizuku.Shizuku

class ActionCapabilityDeviceTest {
    @Test fun shellActionsAndComponentRestrictionsAreVerifiedPerProfile() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        val services = CanaServices.getInstance()
        val pkg = "io.github.jordanwoodson.cana.fixture"
        val system = "com.android.printspooler"
        val componentInfo = Packages.getPackageInfo(system, PackageManager.GET_SERVICES, 0)!!.services!!.first()
        val restrictedComponent = ComponentName(system, componentInfo.name)
        val component = ComponentName(pkg, "com.google.android.gms.measurement.FixtureService")
        assertEquals(0, Packages.getPackageInfo(system, 0, 0)!!.applicationInfo!!.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        suspend fun exec(vararg args: String) {
            val result = services.shell.exec(args.toList())
            assertTrue("${args.toList()}: ${result.message}", result.success)
        }
        fun adb(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
        for ((selected, other) in listOf(0 to 10, 10 to 0)) {
            assertTrue(services.packageOps.reinstall(pkg, selected).success)
            assertTrue(services.packageOps.reinstall(pkg, other).success)
            val original = Packages.applicationEnabledSetting(pkg, selected)
            val peer = Packages.applicationEnabledSetting(pkg, other)
            val componentOriginal = Packages.componentEnabledSetting(component, selected)
            val componentPeer = Packages.componentEnabledSetting(component, other)
            try {
                val restrictedBefore = Packages.componentEnabledSetting(restrictedComponent, selected)
                val rejected = services.shell.exec(listOf("pm", "disable", "--user", "$selected", restrictedComponent.flattenToString()))
                assertFalse("Android 15 shell must refuse non-test component changes", rejected.success)
                assertEquals(restrictedBefore, Packages.componentEnabledSetting(restrictedComponent, selected))
                exec("pm", "disable-user", "--user", "$selected", pkg)
                assertEquals(3, Packages.applicationEnabledSetting(pkg, selected))
                assertEquals(peer, Packages.applicationEnabledSetting(pkg, other))
                exec("pm", "enable", "--user", "$selected", pkg)
                exec("pm", "suspend", "--user", "$selected", pkg)
                assertNotEquals(0, Packages.getPackageInfo(pkg, 0, selected)!!.applicationInfo!!.flags and ApplicationInfo.FLAG_SUSPENDED)
                assertEquals(0, Packages.getPackageInfo(pkg, 0, other)!!.applicationInfo!!.flags and ApplicationInfo.FLAG_SUSPENDED)
                exec("pm", "unsuspend", "--user", "$selected", pkg)
                exec("pm", "disable", "--user", "$selected", component.flattenToString())
                assertEquals(2, Packages.componentEnabledSetting(component, selected))
                assertEquals(componentPeer, Packages.componentEnabledSetting(component, other))
                adb("run-as $pkg --user $selected mkdir -p files")
                adb("run-as $pkg --user $selected touch files/cana-retained")
                assertTrue(adb("run-as $pkg --user $selected ls files").contains("cana-retained"))
                val removed = services.packageOps.uninstallStep(pkg, selected, 1)
                assertTrue(removed.message, removed.success)
                assertTrue(services.packageOps.reinstall(pkg, selected).success)
                assertTrue("DELETE_KEEP_DATA must preserve the marker", adb("run-as $pkg --user $selected ls files").contains("cana-retained"))
            } finally {
                exec("pm", "unsuspend", "--user", "$selected", pkg)
                exec("pm", if (original == 0) "default-state" else "enable", "--user", "$selected", pkg)
                exec("pm", if (componentOriginal == 0) "default-state" else "enable", "--user", "$selected", component.flattenToString())
                services.packageOps.reinstall(pkg, selected)
            }
        }
    }
}
