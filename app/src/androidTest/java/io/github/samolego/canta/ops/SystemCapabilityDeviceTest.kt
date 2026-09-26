package io.github.samolego.canta.ops

import android.os.Build
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import rikka.shizuku.Shizuku

class SystemCapabilityDeviceTest {
    @Test fun selfGrantedGlobalSettingsAndShellDataSaverRoundtrip() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        val services = CanaServices.getInstance()
        services.selfGrants.grantMissing()
        assertTrue(services.selfGrants.status().secureSettings)
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = linkedMapOf("private_dns_mode" to "hostname", "private_dns_specifier" to "dns.quad9.net",
            "captive_portal_mode" to "1", "captive_portal_http_url" to "http://connectivitycheck.grapheneos.network/generate_204",
            "captive_portal_https_url" to "https://connectivitycheck.grapheneos.network/generate_204",
            "captive_portal_fallback_url" to "http://connectivitycheck.grapheneos.network/generate_204",
            "captive_portal_other_fallback_urls" to "", "captive_portal_use_https" to "1",
            "wifi_scan_always_enabled" to "1", "ble_scan_always_enabled" to "0", "mobile_data_always_on" to "0")
        val before = values.mapValues { Settings.Global.getString(resolver, it.key) }
        val saver = services.shell.exec(listOf("cmd", "netpolicy", "get", "restrict-background"))
        assertTrue(saver.message, saver.success)
        val wasEnabled = saver.stdout.trim() == "Restrict background status: enabled"
        try {
            for ((key, value) in values) {
                assertTrue("Write $key", Settings.Global.putString(resolver, key, value))
                assertEquals(key, value, Settings.Global.getString(resolver, key))
            }
            val changed = services.shell.exec(listOf("cmd", "netpolicy", "set", "restrict-background", (!wasEnabled).toString()))
            assertTrue(changed.message, changed.success)
            val after = services.shell.exec(listOf("cmd", "netpolicy", "get", "restrict-background"))
            assertEquals("Restrict background status: ${if (!wasEnabled) "enabled" else "disabled"}", after.stdout.trim())
        } finally {
            for ((key, value) in before) {
                assertTrue(Settings.Global.putString(resolver, key, value))
                assertEquals("Restore $key", value, Settings.Global.getString(resolver, key))
            }
            assertTrue(services.shell.exec(listOf("cmd", "netpolicy", "set", "restrict-background", wasEnabled.toString())).success)
        }
    }
}
