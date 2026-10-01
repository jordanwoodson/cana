package io.github.samolego.canta.ops

import org.json.JSONArray
import org.json.JSONObject

object PrivacyUiPolicy {
    fun preselectedPermissionNames(entries: JSONArray): Set<String> = (0 until entries.length())
        .map { entries.getJSONObject(it) }
        .filter { it.getBoolean("granted") && PrivacyPolicy.mutablePermission(it.getInt("flags")) }
        .map { it.getString("name") }.toSet()

    fun isRestricted(action: PrivacyAction, value: JSONObject): Boolean = when (action) {
        PrivacyAction.PERMISSIONS -> false
        PrivacyAction.BACKGROUND -> value.optString("runAnyInBackground") in setOf("ignore", "deny", "foreground")
        PrivacyAction.METERED -> value.optInt("meteredPolicy") and 1 != 0
        PrivacyAction.NETWORK -> value.optBoolean("chainEnabled") && value.optInt("networkRule") == 2
    }
}
