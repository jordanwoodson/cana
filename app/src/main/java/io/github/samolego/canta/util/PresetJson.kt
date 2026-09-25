package io.github.samolego.canta.util

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object PresetJson {
    fun encode(preset: CantaPresetData): String = JSONObject().apply {
        put("name", preset.name); put("description", preset.description); put("createdDate", preset.createdDate)
        put("version", preset.version); put("apps", JSONArray(preset.apps.toList()))
        if (preset.lockdown.isNotEmpty()) put("lockdown", JSONArray().apply {
            preset.lockdown.forEach { entry -> put(JSONObject().put("packageName", entry.packageName)
                .put("revokePermissions", entry.revokePermissions).put("restrictBackground", entry.restrictBackground)
                .put("denyMetered", entry.denyMetered).put("blockNetwork", entry.blockNetwork)) }
        })
    }.toString(2)

    fun decode(value: String): CantaPresetData {
        require(value.length <= 2_000_000)
        val json = JSONObject(value)
        val apps = json.getJSONArray("apps")
        require(apps.length() <= 5000)
        fun packageName(name: String): String {
            require(name.length <= 256 && Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*").matches(name)) { "Invalid package name" }
            return name
        }
        val removed = (0 until apps.length()).map { index ->
            val entry = apps.get(index)
            packageName(if (entry is JSONObject) entry.getString("packageName") else entry as String)
        }.toSet()
        val lockdown = json.optJSONArray("lockdown") ?: JSONArray()
        require(lockdown.length() <= 5000)
        val settings = (0 until lockdown.length()).map { index -> lockdown.getJSONObject(index).let {
            LockdownSettings(packageName(it.getString("packageName")), it.optBoolean("revokePermissions"),
                it.optBoolean("restrictBackground"), it.optBoolean("denyMetered"), it.optBoolean("blockNetwork"))
        } }
        require(settings.map { it.packageName }.distinct().size == settings.size)
        require(settings.none { it.packageName in removed }) { "A removed app cannot also be locked down" }
        return CantaPresetData(json.getString("name").also { require(it.isNotBlank() && it.length <= 256) },
            json.optString("description", ""), json.optLong("createdDate", 0), removed,
            json.optString("version", "1.0"), UUID.randomUUID().toString(), settings)
    }
}
