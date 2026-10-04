package io.github.samolego.canta.ops

import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.RemovalRecommendation.*
import io.github.samolego.canta.util.apps.UserProfile.Kind
import org.junit.Assert.*
import org.junit.Test

class WorkProfileGuidanceTest {
    private val recommended = BloatData(null, "General guidance", RECOMMENDED)
    private fun guidance(pkg: String, data: BloatData? = recommended,
        safety: SafetyAssessment = SafetyAssessment(), dependents: List<String> = emptyList()) =
        WorkProfileGuidance.assess(pkg, Kind.WORK, data, safety, dependents)!!

    @Test fun personalAndOtherProfileKindsKeepOriginalListGuidance() {
        for (kind in Kind.entries.filter { it != Kind.WORK }) {
            assertNull(WorkProfileGuidance.assess("com.google.android.youtube", kind, recommended, SafetyAssessment()))
        }
    }
    @Test fun personalEntertainmentAndWorkInfrastructureHaveDifferentSuggestions() {
        assertEquals(RECOMMENDED, guidance("com.google.android.youtube").recommendation)
        assertEquals(WorkNeed.OPTIONAL, guidance("com.google.android.youtube").need)
        for (pkg in listOf("com.google.android.gms", "com.android.vending", "com.google.android.webview",
            "com.android.providers.downloads", "com.android.managedprovisioning", "net.typeblog.shelter")) {
            assertEquals(pkg, WorkNeed.KEEP, guidance(pkg).need)
            assertNotEquals(pkg, RECOMMENDED, guidance(pkg).recommendation)
        }
    }
    @Test fun activeRolesAdminsKeyboardsAndInstalledDependentsOverrideOptionalAdvice() {
        for (kind in listOf("admin", "keyboard", "role")) {
            assertEquals(WorkNeed.KEEP, guidance("com.google.android.youtube", safety =
                SafetyAssessment(warnings = listOf(SafetyWarning(kind, "active")))).need)
        }
        val needed = guidance("com.google.android.youtube", dependents = listOf("work.client"))
        assertEquals(WorkNeed.KEEP, needed.need)
        assertEquals(listOf("work.client"), needed.details)
    }
    @Test fun unknownWorkNeedsAndFailedSafetyQueriesNeverBecomeRemovalRecommendations() {
        assertEquals(WorkNeed.REVIEW, guidance("unknown.package", data = null).need)
        assertEquals(WorkNeed.REVIEW, guidance("unknown.package").need)
        assertEquals(WorkNeed.REVIEW, guidance("com.google.android.youtube", safety = SafetyAssessment(error = "Unavailable")).need)
        assertNotEquals(RECOMMENDED, guidance("com.google.android.youtube", safety = SafetyAssessment(error = "Unavailable")).recommendation)
    }
    @Test fun upstreamUnsafeAndExpertAdviceIsNeverDowngradedByOptionalWorkAdvice() {
        for (risk in listOf(UNSAFE, EXPERT)) {
            assertEquals(risk, guidance("com.google.android.youtube", data = recommended.copy(removal = risk)).recommendation)
        }
    }
    @Test fun dependenciesAreCheckedInBothDirectionsWithinSelectedProfileOnly() {
        val metadata = mapOf("service" to recommended.copy(neededBy = listOf("personal.client")),
            "work.client" to recommended.copy(dependencies = listOf("service")))
        assertEquals(listOf("work.client"), WorkProfileGuidance.installedDependents("service", metadata, setOf("service", "work.client")))
        assertEquals(listOf("personal.client"), WorkProfileGuidance.installedDependents("service", metadata, setOf("service", "personal.client")))
        assertTrue(WorkProfileGuidance.installedDependents("service", metadata, setOf("service")).isEmpty())
    }
}
