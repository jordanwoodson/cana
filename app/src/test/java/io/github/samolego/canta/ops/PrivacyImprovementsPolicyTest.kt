package io.github.samolego.canta.ops

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PrivacyImprovementsPolicyTest {
    @Test fun undoRequiresFreshConsentOnlyWhenItReintroducesRestrictions() {
        assertTrue(PrivacyReviewPolicy.undoNeedsConsent("network", JSONObject("{\"networkRule\":2}"),
            JSONObject("{\"networkRule\":1,\"chainEnabled\":true}")))
        assertFalse(PrivacyReviewPolicy.undoNeedsConsent("network", JSONObject("{\"networkRule\":1}"),
            JSONObject("{\"networkRule\":2,\"chainEnabled\":true}")))
        assertTrue(PrivacyReviewPolicy.undoNeedsConsent("metered", JSONObject("{\"meteredPolicy\":1}"), JSONObject("{\"meteredPolicy\":0}")))
        assertFalse(PrivacyReviewPolicy.undoNeedsConsent("metered", JSONObject("{\"meteredPolicy\":0}"), JSONObject("{\"meteredPolicy\":1}")))
        assertTrue(PrivacyReviewPolicy.undoNeedsConsent("permissions",
            JSONObject("{\"permissions\":[{\"name\":\"camera\",\"granted\":false,\"flags\":2}]}"),
            JSONObject("{\"permissions\":[{\"name\":\"camera\",\"granted\":true,\"flags\":0}]}")))
    }
    @Test fun unchangedReviewedIdentityCanReconcileButChangedIdentityConditionsAndLegacyCannot() {
        assertTrue(PrivacyReviewPolicy.reusable("install-A", "install-A", "browser:A,peer:B", "browser:A,peer:B"))
        assertFalse(PrivacyReviewPolicy.reusable("install-A", "install-B", "browser:A", "browser:A"))
        assertFalse(PrivacyReviewPolicy.reusable("install-A", "install-A", "browser:A", "browser:B"))
        assertFalse(PrivacyReviewPolicy.reusable("", "install-A", "", ""))
    }

    @Test fun explicitInstallationOwnerRemainsSeparateFromTargetProfile() {
        val command = PrivacyCommand.parse(listOf("--owner-user", "11", "network-get", "test.app", "10", "10123"))
        assertEquals(11, command.ownerUserId)
        assertEquals(listOf("network-get", "test.app", "10", "10123"), command.arguments)
        assertEquals(0, PrivacyCommand.parse(listOf("network-get", "test.app", "10", "10123")).ownerUserId)
        for (bad in listOf(listOf("--owner-user"), listOf("--owner-user", "-1", "network-get"))) {
            assertTrue(runCatching { PrivacyCommand.parse(bad) }.isFailure)
        }
    }

    @Test fun selectedPermissionsJournalOnlySelectedMutableValuesAndRejectUnknownChoices() {
        val before = JSONObject("""{"appId":10123,"permissions":[{"name":"camera","flags":1,"granted":true},{"name":"location","flags":0,"granted":true},{"name":"fixed","flags":16,"granted":true}]}""")
        val selected = PermissionChoices.snapshot(before, setOf("camera"))
        assertEquals(1, selected.getJSONArray("permissions").length())
        assertEquals("camera", selected.getJSONArray("permissions").getJSONObject(0).getString("name"))
        assertEquals(setOf("camera"), PermissionChoices.selected(selected))
        assertEquals(3, before.getJSONArray("permissions").length())
        assertTrue(runCatching { PermissionChoices.snapshot(before, setOf("unknown")) }.isFailure)
        assertTrue(runCatching { PermissionChoices.snapshot(before, setOf("fixed")) }.isFailure)
        assertTrue(runCatching { PermissionChoices.snapshot(before, emptySet()) }.isFailure)
        assertNull(PermissionChoices.selected(before))
    }
}
