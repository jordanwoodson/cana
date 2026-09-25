package io.github.samolego.canta.ops

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku

/** Run explicitly with adb -s emulator-5554; requires scripts/create-test-fixture.sh and Work user 10. */
@RunWith(AndroidJUnit4::class)
class FoundationDeviceTest {
    private val services get() = CanaServices.getInstance()

    @Before fun emulatorAndShizukuOnly() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "These tests must only run on an emulator" }
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        assertEquals(PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission())
    }

    @Test fun shellServiceRunsAsShellAndReportsRealErrors() = runBlocking {
        val identity = services.shell.exec(listOf("id"))
        assertTrue(identity.message, identity.success)
        assertTrue(identity.stdout, identity.stdout.contains("uid=2000"))
        val invalid = services.shell.exec(listOf("cmd", "package", "cana-nonexistent-command"))
        assertFalse(invalid.success)
        assertTrue(invalid.message.isNotBlank())
        assertEquals(126, services.shell.exec(listOf("sh", "-c", "echo forbidden")).exitCode)
    }

    @Test fun selfGrantsAreVerifiedAndNotRepeated() = runBlocking {
        val results = services.selfGrants.grantMissing()
        assertTrue(results.toString(), results.all { it.success })
        assertTrue(services.selfGrants.status().secureSettings)
        assertTrue(services.selfGrants.status().usageStats)
        val count = services.history.records.first().size
        assertTrue(services.selfGrants.grantMissing().isEmpty())
        assertEquals(count, services.history.records.first().size)
    }

    @Test fun factoryResetRemovesUpdateAndOnlyUninstallsSelectedUser() = runBlocking {
        val name = "com.android.printspooler"
        assertTrue(services.packageOps.reinstall(name, 0).success)
        assertTrue(services.packageOps.reinstall(name, 10).success)
        for ((userId, otherUser) in listOf(0 to 10, 10 to 0)) {
            val update = services.shell.exec(listOf("pm", "install", "-r", "/data/local/tmp/cana-printspooler.apk"), 30_000)
            assertTrue(update.message, update.success)
            assertTrue(services.packageOps.canResetToFactory(name, userId))
            val result = services.packageOps.uninstall(name, userId, resetToFactory = true,
                approvedDowngradeUsers = setOf(otherUser))
            assertTrue(result.message, result.success)
            assertFalse(installed(name, userId))
            assertTrue(installed(name, otherUser))
            assertFalse(services.packageOps.canResetToFactory(name, userId))
            val record = services.history.records.first().last { it.packageName == name }
            assertTrue(record.completed && record.success && record.changed)
            assertEquals(userId, record.userId)
            assertTrue(record.previousState.contains("\"updatedSystemApp\":true"))
            assertTrue(services.packageOps.reinstall(name, userId).success)
        }
    }

    @Test fun userAppRemovalIsScopedToPersonalAndWorkProfiles() = runBlocking {
        val name = "io.github.jordanwoodson.cana.fixture"
        assertTrue("Install the fixture before running this test", installed(name, 0))
        assertTrue(services.packageOps.reinstall(name, 10).success)
        val personal = services.packageOps.uninstall(name, 0)
        assertTrue(personal.message, personal.success)
        assertFalse(installed(name, 0))
        assertTrue(installed(name, 10))
        assertTrue(services.packageOps.reinstall(name, 0).success)
        val work = services.packageOps.uninstall(name, 10)
        assertTrue(work.message, work.success)
        assertFalse(installed(name, 10))
        assertTrue(installed(name, 0))
        assertTrue(services.packageOps.reinstall(name, 10).success)
    }

    @Test fun missingPackageFailureIsPersistedAndCounted() = runBlocking {
        val result = services.packageOps.uninstall("cana.nonexistent.package", 10)
        assertFalse(result.success)
        assertEquals(1, BatchResult(listOf(result)).failureCount)
        val record = services.history.records.first().last { it.packageName == "cana.nonexistent.package" }
        assertTrue(record.completed)
        assertFalse(record.success)
        assertTrue(record.resultMessage.isNotBlank())
    }

    private fun installed(name: String, userId: Int): Boolean =
        ShizukuPackageInstallerUtils.getPackageInfo(name, PackageManager.MATCH_UNINSTALLED_PACKAGES, userId)
            ?.applicationInfo?.let { it.flags and ApplicationInfo.FLAG_INSTALLED != 0 } == true
}
