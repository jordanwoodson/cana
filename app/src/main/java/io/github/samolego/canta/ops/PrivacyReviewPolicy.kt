package io.github.samolego.canta.ops

import org.json.JSONObject
import org.json.JSONArray

/** Consent belongs to an installation identity and the exact reviewed safety conditions. */
internal object PrivacyReviewPolicy {
    fun reusable(savedIdentity: String, identity: String, savedConditions: String, conditions: String): Boolean =
        savedIdentity.isNotEmpty() && savedIdentity == identity && savedConditions == conditions
    fun undoNeedsConsent(action: String, prior: JSONObject, current: JSONObject): Boolean = when (action) {
        "network" -> prior.getInt("networkRule") == 2 &&
            (current.getInt("networkRule") != 2 || !current.optBoolean("chainEnabled"))
        "metered" -> prior.getInt("meteredPolicy") and 1 != 0 && current.getInt("meteredPolicy") and 1 == 0
        "background" -> (prior.getString("runAnyInBackground") in setOf("ignore", "deny") &&
            current.getString("runAnyInBackground") !in setOf("ignore", "deny")) ||
            (prior.getInt("standbyBucket") >= 45 && current.getInt("standbyBucket") < 45)
        "permissions" -> {
            val before = prior.getJSONArray("permissions")
            val now = current.getJSONArray("permissions")
            val byName = (0 until now.length()).map { now.getJSONObject(it) }.associateBy { it.getString("name") }
            (0 until before.length()).map { before.getJSONObject(it) }.any { permission ->
                val actual = byName[permission.getString("name")]
                actual != null && PrivacyPolicy.mutablePermission(permission.getInt("flags")) &&
                    ((!permission.getBoolean("granted") && actual.getBoolean("granted")) ||
                        (permission.getInt("flags") and 2 != 0 && actual.getInt("flags") and 2 == 0))
            }
        }
        else -> false
    }
}

internal data class PrivacyCommand(val ownerUserId: Int, val arguments: List<String>) {
    companion object {
        fun parse(arguments: List<String>): PrivacyCommand {
            if (arguments.firstOrNull() != "--owner-user") return PrivacyCommand(0, arguments)
            require(arguments.size >= 3) { "Missing installation owner or command" }
            val owner = arguments[1].toInt()
            require(owner >= 0) { "Invalid installation owner" }
            return PrivacyCommand(owner, arguments.drop(2))
        }
    }
}

internal object PermissionChoices {
    fun snapshot(state: JSONObject, selected: Set<String>?): JSONObject {
        if (selected == null) return state
        require(selected.isNotEmpty()) { "Select at least one permission" }
        val entries = state.getJSONArray("permissions")
        val available = (0 until entries.length()).map { entries.getJSONObject(it) }
        require(selected.all { name -> available.any { it.getString("name") == name && PrivacyPolicy.mutablePermission(it.getInt("flags")) } }) {
            "A selected permission is unavailable or fixed by policy"
        }
        return JSONObject(state.toString()).put("permissions", JSONArray().apply {
            available.filter { it.getString("name") in selected }.forEach { put(it) }
        }).put("selectedPermissions", JSONArray(selected.sorted()))
    }
    fun selected(state: JSONObject): Set<String>? = state.optJSONArray("selectedPermissions")?.let { entries ->
        (0 until entries.length()).map { entries.getString(it) }.toSet()
    }
}
