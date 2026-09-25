package io.github.samolego.canta.ops

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import rikka.shizuku.Shizuku
import java.io.File
import java.util.UUID

class RecoveryDeviceTest {
    @Test fun generatedScriptRestoresRemovedWorkAppWithoutTouchingPersonalProfile() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        val services = CanaServices.getInstance()
        val name = "com.android.printspooler"
        assertTrue(services.packageOps.reinstall(name, 0).success)
        assertTrue(services.packageOps.reinstall(name, 10).success)
        val batchId = UUID.randomUUID().toString()
        try {
            val result = services.packageOps.uninstall(name, 10, batchId = batchId)
            assertTrue(result.message, result.success)
            val records = services.history.records.first().filter { it.batchId == batchId }
            assertEquals(1, records.size)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val path = File(instrumentation.targetContext.getExternalFilesDir(null), "cana-test-restore.sh")
            path.writeText(RestoreScript.generate(records))
            // UiAutomation tokenizes argv itself; it does not interpret shell quoting.
            val descriptor = instrumentation.uiAutomation.executeShellCommand("sh ${path.absolutePath}")
            val output = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
            assertTrue(output, output.contains("0 failed commands; 0 manual steps"))
            for (user in listOf(0, 10)) {
                val app = services.packageOps.getPackageInfo(name, user)!!.applicationInfo!!
                assertNotEquals(0, app.flags and ApplicationInfo.FLAG_INSTALLED)
            }
        } finally { services.packageOps.reinstall(name, 10) }
    }
}
