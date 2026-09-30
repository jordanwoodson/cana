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
    val apkIdentity: String = "",
) {
    fun isFor(versionCode: Long, apkIdentity: String): Boolean =
        this.versionCode == versionCode && (apkIdentity.isNotEmpty() || error != null) && this.apkIdentity == apkIdentity
}

/** JSON persistence for scan results, tied to the signature set they were computed with. */
object TrackerScanCache {
    private const val VERSION = 2

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
                val scan = runCatching {
                    val trackers = item.getJSONArray("trackers")
                    val names = (0 until trackers.length()).map { index ->
                        (trackers.get(index) as? String)?.takeIf { it.isNotBlank() && it.length <= 256 }
                            ?: error("Invalid tracker name")
                    }
                    val version = (item.get("versionCode") as? Number)?.toLong() ?: error("Invalid version")
                    val classes = item.getInt("classes")
                    val apks = item.getInt("apks")
                    val time = item.getLong("scannedAt")
                    val identity = item.getString("apkIdentity")
                    val error = if (item.has("error")) item.getString("error").takeIf { it.isNotBlank() } else null
                    require(version >= 0 && classes >= 0 && apks >= 0 && time > 0)
                    require(names.size <= 5000 && key.isNotBlank())
                    require(error != null || (apks > 0 && identity.matches(Regex("[0-9a-f]{64}"))))
                    TrackerScan(key, version, names, classes, apks, time, error, identity)
                }.getOrNull() ?: continue
                put(key, scan)
            }
        }
    }

    fun serialize(signatureKey: String, scans: Map<String, TrackerScan>): String {
        val all = JSONObject()
        for (scan in scans.values) {
            all.put(scan.packageName, JSONObject().put("versionCode", scan.versionCode).put("trackers", JSONArray(scan.trackers))
                .put("classes", scan.classCount).put("apks", scan.apkCount).put("scannedAt", scan.scannedAt).put("apkIdentity", scan.apkIdentity)
                .apply { scan.error?.let { put("error", it) } })
        }
        return JSONObject().put("version", VERSION).put("signatures", signatureKey).put("scans", all).toString()
    }
}
