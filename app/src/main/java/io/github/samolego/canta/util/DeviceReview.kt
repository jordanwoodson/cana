package io.github.samolego.canta.util

import org.json.JSONArray
import org.json.JSONObject

/** The bundled Metro Motorola Edge 2025 review (docs/package-audit/2026-10-01/motorola_packages.json). */
internal const val DEVICE_REVIEW_ASSET = "moto-edge-2025.json"

private val LISTS = setOf("Aosp", "Carrier", "Google", "Misc", "Oem")
private val REMOVALS = setOf("Recommended", "Advanced", "Expert", "Unsafe")
private val PACKAGE_ID = Regex("^(?:android|[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+)$")
private val REVIEW_FIELDS = listOf("list", "description", "source", "dependencies", "neededBy", "labels", "suggestions", "verdict")
private val UPSTREAM_FIELDS = listOf("label", "warning", "web", "suggestions", "alternatives")

/**
 * Merge a package-keyed device review over an upstream list, exactly as tools/debloat-server does for
 * the served list: the review decides the rating, description, source, relations and Cana's verdict;
 * upstream's display name, warning, references and replacement records stay. Packages only the review
 * knows (user apps, device-specific identities) are added.
 */
internal fun applyDeviceReview(upstream: JSONObject, review: JSONObject): JSONObject {
    require(review.length() > 0) { "Device review is empty" }
    val result = JSONObject(upstream.toString())
    for (id in review.keys()) {
        val item = requireNotNull(review.optJSONObject(id)) { "Invalid device review entry: $id" }
        require(PACKAGE_ID.matches(id)) { "Invalid package ID in device review: $id" }
        require(item.optString("list") in LISTS) { "Unknown list for $id" }
        require(item.optString("removal") in REMOVALS) { "Unknown removal for $id" }
        for (field in listOf("description", "source")) {
            require((item.opt(field) as? String)?.isNotBlank() == true) { "$id needs a nonempty $field" }
        }
        for (field in listOf("dependencies", "neededBy", "labels")) {
            val values = requireNotNull(item.optJSONArray(field)) { "$id $field must be a string array" }
            for (index in 0 until values.length()) {
                require((values.opt(index) as? String)?.isNotBlank() == true) { "$id $field contains an invalid value" }
            }
        }
        item.opt("verdict")?.let { verdict ->
            require(verdict is JSONObject && verdict.optString("action") in setOf("Keep", "Uninstall") &&
                verdict.optJSONArray("benefits") != null && verdict.optString("reason").isNotBlank()) { "$id has an invalid verdict" }
        }
        val merged = JSONObject()
        for (field in REVIEW_FIELDS) item.opt(field)?.let { merged.put(field, it) }
        result.optJSONObject(id)?.let { current ->
            for (field in UPSTREAM_FIELDS) {
                if (!merged.has(field)) current.opt(field)?.let { merged.put(field, it) }
            }
        }
        merged.put("removal", item.getString("removal"))
        for (field in listOf("dependencies", "neededBy", "labels")) {
            if (!merged.has(field)) merged.put(field, JSONArray())
        }
        result.put(id, merged)
    }
    return result
}
