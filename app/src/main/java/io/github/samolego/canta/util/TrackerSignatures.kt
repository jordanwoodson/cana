package io.github.samolego.canta.util

import org.json.JSONObject

data class TrackerSignature(val name: String, val prefixes: List<String>)

object TrackerSignatures {
    fun parse(json: JSONObject): List<TrackerSignature> {
        val trackers = json.optJSONObject("trackers") ?: throw IllegalArgumentException("Expected an Exodus trackers object")
        require(trackers.length() in 1..5000) { "Invalid tracker count" }
        return trackers.keys().asSequence().map { key ->
            val tracker = trackers.optJSONObject(key) ?: throw IllegalArgumentException("Invalid tracker $key")
            val name = tracker.optString("name").trim()
            val code = tracker.opt("code_signature") as? String ?: throw IllegalArgumentException("Missing code_signature")
            require(name.isNotEmpty() && name.length <= 256 && code.length <= 16_384)
            val prefixes = code.split('|').map { it.trim().replace('/', '.') }.filter { it.isNotBlank() }.distinct()
            require(prefixes.size <= 256 && prefixes.all { it.length <= 1024 && it != "." && it.matches(Regex("[A-Za-z_.][A-Za-z0-9_.\\$-]*")) }) {
                "Only literal class prefixes are supported"
            }
            TrackerSignature(name, prefixes)
        }.toList()
    }

    fun matches(className: String, signatures: List<TrackerSignature>): List<String> = signatures
        .filter { tracker -> tracker.prefixes.any {
            if (it.startsWith('.')) className.contains(it) else className.startsWith(it)
        } }.map { it.name }.distinct().sorted()
}
