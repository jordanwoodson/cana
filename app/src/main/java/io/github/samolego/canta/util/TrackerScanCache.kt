package io.github.samolego.canta.util

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** The result of matching one package's APK code against tracker signatures. */
data class TrackerScan(
    val packageName: String,
    val versionCode: Long,
    val trackers: List<String>,
    val classCount: Int,
    val apkCount: Int,
    val scannedAt: Long,
    val error: String? = null,
) {
    fun isFor(versionCode: Long): Boolean = this.versionCode == versionCode
}

/** JSON persistence for scan results, tied to the signature set they were computed with. */
object TrackerScanCache {
    private const val VERSION = 1

    /** Identifies a signature set; results computed with another set are discarded on load. */
    fun signatureKey(catalog: TrackerCatalog): String {
        val text = catalog.signatures.sortedBy { it.name }
            .joinToString("\n") { it.name + "=" + it.prefixes.sorted().joinToString("|") }
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }

    fun parse(text: String, signatureKey: String): Map<String, TrackerScan> {
        val json = try { JSONObject(text) } catch (_: Exception) { return emptyMap() }
        if (json.optInt("version") != VERSION || json.optString("signatures") != signatureKey) return emptyMap()
        val scans = json.optJSONObject("scans") ?: return emptyMap()
        return buildMap {
            for (key in scans.keys()) {
                val item = scans.optJSONObject(key) ?: continue
                val trackers = item.optJSONArray("trackers") ?: JSONArray()
                put(key, TrackerScan(key, item.optLong("versionCode"),
                    (0 until trackers.length()).mapNotNull { trackers.optString(it).takeIf(String::isNotBlank) },
                    item.optInt("classes"), item.optInt("apks"), item.optLong("scannedAt"),
                    item.optString("error").takeIf { it.isNotBlank() }))
            }
        }
    }

    fun serialize(signatureKey: String, scans: Map<String, TrackerScan>): String {
        val all = JSONObject()
        for (scan in scans.values) {
            all.put(scan.packageName, JSONObject().put("versionCode", scan.versionCode).put("trackers", JSONArray(scan.trackers))
                .put("classes", scan.classCount).put("apks", scan.apkCount).put("scannedAt", scan.scannedAt)
                .apply { scan.error?.let { put("error", it) } })
        }
        return JSONObject().put("version", VERSION).put("signatures", signatureKey).put("scans", all).toString()
    }
}
