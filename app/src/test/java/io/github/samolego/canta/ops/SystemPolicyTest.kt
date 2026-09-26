package io.github.samolego.canta.ops

import io.github.samolego.canta.data.proto.OperationRecord
import org.junit.Assert.*
import org.junit.Test

class SystemPolicyTest {
    @Test fun hostnameAcceptsDnsNamesButRejectsUrlsIpsAndInvalidLabels() {
        assertEquals("dns.quad9.net", SystemPolicy.hostname("DNS.QUAD9.NET"))
        assertEquals("xn--bcher-kva.example", SystemPolicy.hostname("bücher.example"))
        for (input in listOf("https://dns.quad9.net", "1.1.1.1", "localhost", "bad name.net", "a..net", "-bad.net", "dns.net/path", "dns.net:853")) {
            assertTrue(input, runCatching { SystemPolicy.hostname(input) }.isFailure)
        }
    }
    @Test fun defaultDnsDeletesOverridesAndCustomModeSetsBothKeys() {
        assertEquals(mapOf("private_dns_mode" to null, "private_dns_specifier" to null), SystemPolicy.dns("default"))
        assertEquals(mapOf("private_dns_mode" to "hostname", "private_dns_specifier" to "dns.quad9.net"), SystemPolicy.dns("hostname", "DNS.QUAD9.NET"))
        assertEquals(mapOf("private_dns_mode" to "opportunistic", "private_dns_specifier" to null), SystemPolicy.dns("opportunistic"))
    }
    @Test fun captivePortalDefaultsDeleteAllOverridesAndAlternativeUsesVerifiedEndpoints() {
        val defaults = SystemPolicy.captivePortal("default")
        assertEquals(SystemControl.CAPTIVE_PORTAL.keys.toSet(), defaults.keys)
        assertTrue(defaults.values.all { it == null })
        val alternative = SystemPolicy.captivePortal("graphene")
        assertEquals("https://connectivitycheck.grapheneos.network/generate_204", alternative["captive_portal_https_url"])
        assertEquals("http://connectivitycheck.grapheneos.network/generate_204", alternative["captive_portal_http_url"])
        assertEquals("1", alternative["captive_portal_use_https"])
        assertEquals(mapOf("captive_portal_mode" to "0"), SystemPolicy.captivePortal("off"))
    }
    @Test fun dataSaverParserRefusesUnexpectedOutput() {
        assertTrue(SystemPolicy.dataSaver("Restrict background status: enabled\n"))
        assertFalse(SystemPolicy.dataSaver("Restrict background status: disabled"))
        assertTrue(runCatching { SystemPolicy.dataSaver("Permission denied") }.isFailure)
    }
    @Test fun recoveryRestoresAbsentHttpsFlagAndPriorDataSaver() {
        val record = OperationRecord.newBuilder().setId("system").setUserId(0).setAction("system")
            .setPreviousState("{\"settings\":{\"captive_portal_use_https\":null},\"dataSaver\":false}").build()
        val plan = RestoreScript.plan(record)
        assertTrue(plan.notes.toString(), plan.notes.isEmpty())
        assertTrue(plan.commands.contains(listOf("settings", "delete", "global", "captive_portal_use_https")))
        assertTrue(plan.commands.contains(listOf("cmd", "netpolicy", "set", "restrict-background", "false")))
    }
}
