package io.github.samolego.canta.ops

import org.junit.Assert.*
import org.junit.Test

class SafetyPolicyTest {
    @Test fun coreAndResolvedOemPackagesCannotBeOverridden() {
        val required = setOf("io.github.jordanwoodson.cana", "moe.shizuku.privileged.api", "com.android.shell",
            "android", "com.android.systemui", "com.android.settings", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.android.permissioncontroller", "com.google.android.permissioncontroller")
        val protected = SafetyPolicy.protectedPackages("io.github.jordanwoodson.cana") + "oem.installer"
        assertTrue(protected.containsAll(required))
        for (name in required + "oem.installer") {
            val report = SafetyPolicy.assess(name, SafetySnapshot(protectedPackages = protected), emptyList())
            assertTrue(report.protected)
            assertFalse(report.permits(setOf("override")))
        }
    }

    @Test fun rolesKeyboardsAdminsAndInstalledDependentsNeedFreshExplicitConsent() {
        val state = SafetySnapshot(roles = mapOf("HOME" to setOf("app"), "SMS" to setOf("app")),
            keyboards = setOf("app"), admins = setOf("app"), installedPackages = setOf("dependent", "app"))
        val report = SafetyPolicy.assess("app", state, listOf("dependent", "absent", "dependent"))
        assertEquals(setOf("0:app:role:HOME", "0:app:role:SMS", "0:app:keyboard:app", "0:app:admin:app", "0:app:dependent:dependent"), report.warnings.map { it.key }.toSet())
        assertFalse(report.permits(emptySet()))
        assertFalse(report.permits(setOf("role:HOME")))
        assertTrue(report.permits(report.warnings.map { it.key }.toSet()))
    }

    @Test fun keyboardParserHandlesSubtypeIdsNullAndDuplicates() {
        assertEquals(setOf("current.ime", "other.ime"), SafetyPolicy.keyboardPackages(
            "current.ime/.Keyboard", "current.ime/.Keyboard;123;456:other.ime/.Service;0"))
        assertEquals(emptySet<String>(), SafetyPolicy.keyboardPackages("null", ""))
    }

    @Test fun failedQueryNeverPermitsMutationButUnrelatedAppDoes() {
        assertFalse(SafetyAssessment(error = "Permission denied").permits(emptySet()))
        assertTrue(SafetyPolicy.assess("ordinary", SafetySnapshot(), emptyList()).permits(emptySet()))
    }
}
