package io.github.samolego.canta.ops

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyUiPolicyTest {
    @Test fun preselectsOnlyGrantedMutablePermissions() {
        val entries = JSONArray("""[
            {"name":"camera","flags":0,"granted":true},
            {"name":"notifications","flags":0,"granted":false},
            {"name":"managed","flags":16,"granted":true}
        ]""")
        assertEquals(setOf("camera"), PrivacyUiPolicy.preselectedPermissionNames(entries))
    }

    @Test fun switchTracksEachLiveRestriction() {
        assertFalse(PrivacyUiPolicy.isRestricted(PrivacyAction.BACKGROUND, JSONObject("""{"runAnyInBackground":"allow"}""")))
        assertTrue(PrivacyUiPolicy.isRestricted(PrivacyAction.BACKGROUND, JSONObject("""{"runAnyInBackground":"foreground"}""")))
        assertTrue(PrivacyUiPolicy.isRestricted(PrivacyAction.METERED, JSONObject("""{"meteredPolicy":1}""")))
        assertFalse(PrivacyUiPolicy.isRestricted(PrivacyAction.METERED, JSONObject("""{"meteredPolicy":0}""")))
        assertTrue(PrivacyUiPolicy.isRestricted(PrivacyAction.NETWORK, JSONObject("""{"chainEnabled":true,"networkRule":2}""")))
        assertFalse(PrivacyUiPolicy.isRestricted(PrivacyAction.NETWORK, JSONObject("""{"chainEnabled":false,"networkRule":2}""")))
    }
}
