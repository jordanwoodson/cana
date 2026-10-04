package io.github.samolego.canta.ops

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import io.github.samolego.canta.util.apps.AppInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku

@RunWith(AndroidJUnit4::class)
class UpdateCleanupDeviceTest {
    @Test fun cleanupRefusesSharedUpdatesEvenWithOldConsentAndCleansOnlyUnusedUpdates() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        val services = CanaServices.getInstance()
        val ops = services.packageOps
        val name = "com.android.printspooler"
        try {
            for ((selected, other) in listOf(0 to 10, 10 to 0)) {
                assertTrue(ops.reinstall(name, selected).success)
                assertTrue(ops.reinstall(name, other).success)
                assertTrue(services.shell.exec(listOf("pm", "install", "-r", "/data/local/tmp/cana-printspooler.apk"), 30_000).success)
                assertTrue(ops.uninstall(name, selected).success)
                val info = ops.getPackageInfo(name, selected)!!
                val display = AppInfo.fromPackageInfo(info, InstrumentationRegistry.getInstrumentation().targetContext.packageManager,
                    isUninstalled = true, otherUser = selected != 0)
                assertTrue(display.isUpdatedSystemApp)
                assertTrue("Update APK size must be readable", display.updateSizeBytes > 0)
                val impact = ops.inspectUpdates(name, selected)
                assertNull(impact.error)
                assertEquals(listOf(other), impact.otherInstalledProfiles.map { it.id })
                val denied = ops.removeUpdates(name, selected)
                assertFalse(denied.success)
                assertFalse(denied.changed)
                assertTrue(ops.canResetToFactory(name, selected))
                val removed = ops.removeUpdates(name, selected, setOf(other))
                assertFalse(removed.message, removed.success)
                assertFalse(removed.changed)
                assertEquals(0L, removed.freedBytes)
                assertTrue(ops.inspectUpdates(name, selected).updated)
                assertFalse(ops.inspectUpdates(name, selected).installed)
                assertTrue(ops.inspectUpdates(name, other).installed)
                assertTrue(ops.inspectUpdates(name, other).updated)
                val record = services.history.records.first().last()
                assertEquals("remove_updates", record.action)
                assertTrue(record.completed && !record.success && !record.changed)
                assertEquals(selected, record.userId)
            }
            assertTrue(ops.reinstall(name, 0).success)
            assertTrue(ops.reinstall(name, 10).success)
            assertTrue(services.shell.exec(listOf("pm", "install", "-r", "/data/local/tmp/cana-printspooler.apk"), 30_000).success)
            assertTrue(ops.uninstall(name, 0).success)
            assertTrue(ops.uninstall(name, 10).success)
            val size = ops.inspectUpdates(name, 0).sizeBytes
            val clean = ops.removeUpdates(name, 0)
            assertTrue(clean.message, clean.success)
            assertTrue(size > 0)
            assertEquals(size, clean.freedBytes)
            assertEquals(size, services.history.records.first().last().freedBytes)
            assertFalse(ops.inspectUpdates(name, 0).installed)
            assertFalse(ops.inspectUpdates(name, 10).installed)
        } finally {
            ops.reinstall(name, 0)
            ops.reinstall(name, 10)
        }
    }

    @Test fun rawResetCannotBypassTheProfileGuard() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        assertEquals(PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission())
        val services = CanaServices.getInstance()
        val name = "com.android.printspooler"
        for ((selected, other) in listOf(0 to 10, 10 to 0)) {
            assertTrue(services.packageOps.reinstall(name, selected).success)
            assertTrue(services.packageOps.reinstall(name, other).success)
            val staged = services.shell.exec(listOf("pm", "install", "-r", "/data/local/tmp/cana-printspooler.apk"), 30_000)
            assertTrue(staged.message, staged.success)
            assertTrue(services.packageOps.uninstall(name, selected).success)
            assertTrue(services.packageOps.canResetToFactory(name, selected))
            try {
                services.packageOps.uninstallStep(name, selected, 0, allowUpdateReset = true)
                fail("Shared update reset must be refused before it reaches Android")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().contains("shared"))
            }
            assertTrue(services.packageOps.inspectUpdates(name, selected).updated)
            assertTrue(services.packageOps.inspectUpdates(name, other).updated)
            assertFalse(services.packageOps.inspectUpdates(name, selected).installed)
            assertTrue(services.packageOps.inspectUpdates(name, other).installed)
            assertTrue(services.packageOps.reinstall(name, selected).success)
        }
    }
}
