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
        val keyboardPackage = services.shell.exec(listOf("settings", "--user", "0", "get", "secure", "default_input_method"))
            .let { check(it.success && '/' in it.stdout); it.stdout.trim().substringBefore('/') }
        val workUser = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("workUser")?.toInt() ?: 10
        for (user in listOf(0, workUser)) {
            val reports = services.safety.inspect(listOf("com.android.packageinstaller", keyboardPackage, "io.github.jordanwoodson.cana.fixture"), user)
            reports.forEach { (name, report) -> assertNull("$name user=$user: ${report.error}", report.error) }
            assertTrue(reports.getValue("com.android.packageinstaller").protected)
            assertTrue(reports.getValue("io.github.jordanwoodson.cana.fixture").permits(emptySet()))
            if (user == workUser) {
                val admins = services.safety.activeAdmins(user)
                assertTrue(services.safety.inspect(admins.toList(), user).values.all { it.protected })
            }
        }
        val keyboard = services.safety.inspect(listOf(keyboardPackage), 0).getValue(keyboardPackage)
        assertTrue(keyboard.warnings.any { it.kind == "keyboard" })
        val refused = services.packageOps.uninstall(keyboardPackage, 0)
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
