package io.github.samolego.canta.ops

import android.os.Build
import android.provider.Settings
import android.app.usage.UsageStatsManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku

/** Separate invocation after stopping the emulator's Shizuku server. */
@RunWith(AndroidJUnit4::class)
class UnavailableServiceTest {
    @Test fun missingShizukuFailsAndPersistentGrantsRemainUsable() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        assertFalse("Stop Shizuku before this test", Shizuku.pingBinder())
        val services = CanaServices.getInstance()
        val result = services.shell.exec(listOf("id"))
        assertFalse(result.success)
        assertTrue(result.message.isNotBlank())
        assertTrue(services.selfGrants.status().secureSettings)
        assertTrue(services.selfGrants.status().usageStats)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val key = "cana_permission_test"
        val previous = Settings.Secure.getString(resolver, key)
        try {
            assertTrue(Settings.Secure.putString(resolver, key, "verified"))
            assertEquals("verified", Settings.Secure.getString(resolver, key))
            val usage = context.getSystemService(UsageStatsManager::class.java)
            assertNotNull(usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, 0, System.currentTimeMillis()))
        } finally { Settings.Secure.putString(resolver, key, previous) }
    }
}
