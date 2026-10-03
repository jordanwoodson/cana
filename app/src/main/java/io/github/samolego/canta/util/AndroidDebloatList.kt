package io.github.samolego.canta.util

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

internal const val MAX_BLOAT_LIST_BYTES = 10 * 1024 * 1024
internal const val ANDROID_DEBLOAT_SOURCE = "Android Debloat List (MuntashirAkon)"

/** Read the five lists and their replacements from one upstream revision. Never extract to disk. */
internal fun parseAndroidDebloatArchive(bytes: ByteArray): JSONObject {
    val categories = mapOf("aosp" to "Aosp", "carrier" to "Carrier", "google" to "Google",
        "misc" to "Misc", "oem" to "Oem")
    val removals = mapOf("delete" to "Recommended", "replace" to "Advanced",
        "caution" to "Expert", "unsafe" to "Unsafe")
    val files = mutableMapOf<String, String>()
    var expandedBytes = 0
    var entries = 0
    ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            require(++entries <= 1000) { "Too many files in debloat archive" }
            val contents = zip.readBloatBytes(MAX_BLOAT_LIST_BYTES - expandedBytes)
            expandedBytes += contents.size
            val path = entry.name.substringAfter('/', "")
            if (path in categories.keys.map { "$it.json" } ||
                path.startsWith("suggestions/") && path.endsWith(".json") && path.count { it == '/' } == 1) {
                require(files.put(path, contents.toString(Charsets.UTF_8)) == null) { "Duplicate debloat file: $path" }
            }
            zip.closeEntry()
        }
    }
    val result = JSONObject()
    for ((category, list) in categories) {
        val records = JSONArray(requireNotNull(files["$category.json"]) { "Missing debloat category: $category" })
        for (index in 0 until records.length()) {
            val item = records.getJSONObject(index)
            val id = (item.opt("id") as? String)?.takeIf { it.isNotBlank() }
            require(id != null && !result.has(id)) { "Missing or duplicate debloat package: $id" }
            val removal = requireNotNull(removals[item.optString("removal")]) { "Unknown removal for $id" }
            val description = requireNotNull(item.opt("description") as? String) { "Missing description for $id" }
            val record = JSONObject().put("list", list).put("removal", removal)
                .put("description", description).put("source", ANDROID_DEBLOAT_SOURCE)
                .put("dependencies", item.optJSONArray("dependencies") ?: JSONArray())
                .put("neededBy", item.optJSONArray("required_by") ?: JSONArray())
                .put("labels", item.optJSONArray("tags") ?: JSONArray())
                .put("web", item.optJSONArray("web") ?: JSONArray())
            for (field in listOf("label", "warning")) {
                (item.opt(field) as? String)?.let { record.put(field, it) }
            }
            (item.opt("suggestions") as? String)?.takeIf { it.isNotBlank() }?.let { suggestionId ->
                val alternatives = JSONArray(requireNotNull(files["suggestions/$suggestionId.json"]) {
                    "Missing replacements for $id: $suggestionId"
                })
                for (alternative in 0 until alternatives.length()) {
                    val app = alternatives.getJSONObject(alternative)
                    for (field in listOf("id", "label", "repo")) {
                        require((app.opt(field) as? String)?.isNotBlank() == true) { "Invalid replacement for $id: $field" }
                    }
                }
                record.put("alternatives", alternatives)
            }
            result.put(id, record)
        }
    }
    require(result.length() > 0) { "Debloat archive is empty" }
    return result
}

/** Bound both the downloaded file and the expanded ZIP, including files we do not use. */
internal fun InputStream.readBloatBytes(limit: Int = MAX_BLOAT_LIST_BYTES): ByteArray {
    val bytes = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val size = read(buffer)
        if (size == -1) break
        require(bytes.size() + size <= limit) { "Bloat list exceeds 10 MB" }
        bytes.write(buffer, 0, size)
    }
    return bytes.toByteArray()
}
