package io.github.samolego.canta.ops

import org.junit.Assert.*
import org.junit.Test

class PrivacyPolicyTest {
    @Test fun uidMathRejectsSystemIdsAndOverflowAndKeepsProfilesSeparate() {
        assertEquals(10123, PrivacyPolicy.uid(0, 10123))
        assertEquals(1010123, PrivacyPolicy.uid(10, 10123))
        for ((user, app) in listOf(-1 to 10123, 0 to 1000, 0 to 100000, 30000 to 10123)) {
            assertTrue(runCatching { PrivacyPolicy.uid(user, app) }.isFailure)
        }
    }
    @Test fun appOpsParserPreservesDefaultsAndRejectsUnrecognizedOutput() {
        assertEquals("allow", PrivacyPolicy.appOpMode("No operations."))
        assertEquals("allow", PrivacyPolicy.appOpMode("No operations.\nDefault mode: allow\n"))
        assertEquals("ignore", PrivacyPolicy.appOpMode("No operations.\nDefault mode: ignore"))
        assertEquals("default", PrivacyPolicy.appOpMode("RUN_ANY_IN_BACKGROUND: default"))
        assertTrue(runCatching { PrivacyPolicy.appOpMode("No operations.\nPermission denial") }.isFailure)
        assertEquals("ignore", PrivacyPolicy.appOpMode("RUN_ANY_IN_BACKGROUND: ignore; time=+10s"))
        assertEquals("allow", PrivacyPolicy.appOpMode("Uid mode: RUN_ANY_IN_BACKGROUND: allow\nRUN_ANY_IN_BACKGROUND: ignore"))
        assertTrue(runCatching { PrivacyPolicy.appOpMode("Permission denial") }.isFailure)
    }
    @Test fun policiesParseOnlyExactUidsAndDetectUnrecognizedOutput() {
        assertEquals(setOf(10123, 1010123), PrivacyPolicy.uidList("Restrict background blacklisted UIDs: 10123 1010123"))
        assertTrue(PrivacyPolicy.uidList("Restrict background whitelisted UIDs: none").isEmpty())
        assertTrue(runCatching { PrivacyPolicy.uidList("Permission denied 10123") }.isFailure)
    }
    @Test fun fixedByPolicyOrSystemIsNeverBulkRevoked() {
        assertTrue(PrivacyPolicy.mutablePermission(0))
        assertTrue(PrivacyPolicy.mutablePermission(3))
        assertFalse(PrivacyPolicy.mutablePermission(4))
        assertFalse(PrivacyPolicy.mutablePermission(16))
    }
}
