package io.github.samolego.canta.ops

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PresetCaptureTest {
    @Test fun pendingMeteredIntentSurvivesPresetCapture() {
        val live = PrivacyState(mapOf(PrivacyAction.METERED to JSONObject().put("meteredPolicy", 0)),
            emptyMap(), listOf("test.app"), desiredBlock = false, desiredMetered = true)
        assertTrue(PresetLockdownCapture.capture("test.app", live)!!.denyMetered)
    }
    @Test fun onlyImmutablePermissionsDoNotInventPermissionRestriction() {
        val permissions = JSONArray().put(JSONObject().put("name", "camera").put("flags", 16).put("granted", true))
        val live = PrivacyState(mapOf(PrivacyAction.PERMISSIONS to JSONObject().put("permissions", permissions)),
            emptyMap(), listOf("test.app"), desiredBlock = true)
        assertFalse(PresetLockdownCapture.capture("test.app", live)!!.revokePermissions)
    }
}
