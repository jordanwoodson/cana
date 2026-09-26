package io.github.samolego.canta.ops

import java.net.IDN
import java.util.Locale

enum class SystemControl(val keys: List<String>) {
    PRIVATE_DNS(listOf("private_dns_mode", "private_dns_specifier")),
    CAPTIVE_PORTAL(listOf("captive_portal_mode", "captive_portal_http_url", "captive_portal_https_url", "captive_portal_fallback_url", "captive_portal_other_fallback_urls", "captive_portal_use_https")),
    WIFI_SCAN(listOf("wifi_scan_always_enabled")), BLE_SCAN(listOf("ble_scan_always_enabled")),
    MOBILE_DATA(listOf("mobile_data_always_on")), DATA_SAVER(emptyList());
}

object SystemPolicy {
    fun hostname(value: String): String {
        require(value.isNotBlank() && value.none { it.isWhitespace() }) { "Enter a DNS hostname without a URL, port or spaces" }
        val ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        require(ascii.length <= 253 && ascii.contains('.') && ascii.any { it in 'a'..'z' }) { "Enter a DNS hostname, not an IP address" }
        require(ascii.split('.').all { it.length in 1..63 && Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?").matches(it) }) { "Invalid DNS hostname" }
        return ascii
    }
    fun dns(mode: String, hostname: String): Map<String, String?> {
        require(mode in setOf("default", "off", "opportunistic", "hostname"))
        return mapOf("private_dns_mode" to mode.takeUnless { it == "default" },
            "private_dns_specifier" to if (mode == "hostname") hostname(hostname) else null)
    }
    fun dns(mode: String): Map<String, String?> = dns(mode, "")
    fun captivePortal(mode: String): Map<String, String?> = when (mode) {
        "default" -> SystemControl.CAPTIVE_PORTAL.keys.associateWith { null }
        "off" -> mapOf("captive_portal_mode" to "0")
        "graphene" -> mapOf("captive_portal_mode" to "1", "captive_portal_use_https" to "1",
            "captive_portal_http_url" to "http://connectivitycheck.grapheneos.network/generate_204",
            "captive_portal_https_url" to "https://connectivitycheck.grapheneos.network/generate_204",
            "captive_portal_fallback_url" to "http://connectivitycheck.grapheneos.network/generate_204",
            "captive_portal_other_fallback_urls" to null)
        else -> error("Unknown captive portal configuration")
    }
    fun dataSaver(output: String): Boolean = when (output.trim()) {
        "Restrict background status: enabled" -> true
        "Restrict background status: disabled" -> false
        else -> error("Unrecognized Data Saver state")
    }
}
