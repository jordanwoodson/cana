package io.github.samolego.canta.ops

import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import rikka.shizuku.Shizuku

class SafetyDeviceTest {
    @Test fun probesBothProfilesAndRefusesCorePackagesAndUnconfirmedKeyboard() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        val services = CanaServices.getInstance()
        for (user in listOf(0, 10)) {
            val reports = services.safety.inspect(listOf("com.android.packageinstaller", "com.android.inputmethod.latin", "io.github.jordanwoodson.cana.fixture"), user)
            reports.forEach { (name, report) -> assertNull("$name user=$user: ${report.error}", report.error) }
            assertTrue(reports.getValue("com.android.packageinstaller").protected)
            assertTrue(reports.getValue("io.github.jordanwoodson.cana.fixture").permits(emptySet()))
            assertTrue(services.safety.activeAdmins(user).isEmpty())
        }
        val keyboard = services.safety.inspect(listOf("com.android.inputmethod.latin"), 0).getValue("com.android.inputmethod.latin")
        assertTrue(keyboard.warnings.any { it.kind == "keyboard" })
        val refused = services.packageOps.uninstall("com.android.inputmethod.latin", 0)
        assertFalse(refused.success)
        assertFalse(refused.changed)
        for (name in listOf("android", "com.android.shell", "io.github.jordanwoodson.cana")) {
            val result = services.packageOps.uninstall(name, 0, approvedWarnings = setOf("override"))
            assertFalse(result.success)
            assertFalse(result.changed)
            assertTrue(result.message, result.message.contains("essential"))
        }
    }
}
