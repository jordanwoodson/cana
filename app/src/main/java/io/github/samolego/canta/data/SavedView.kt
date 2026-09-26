package io.github.samolego.canta.data

import org.json.JSONArray
import org.json.JSONObject

data class SavedView(
    val id: String,
    val name: String,
    val userId: Int?,
    val query: String,
    val filterIds: Set<String>,
    val onlySystem: Boolean,
    val sort: String,
    val packages: Set<String> = emptySet(),
) {
    fun appliesTo(user: Int) = userId == null || userId == user
}

object SavedViewJson {
    fun encode(views: List<SavedView>): String = JSONObject().put("schema", 1).put("views", JSONArray().apply {
        views.forEach { view -> put(JSONObject().put("id", view.id).put("name", view.name)
            .put("user", view.userId ?: JSONObject.NULL).put("query", view.query)
            .put("filters", JSONArray(view.filterIds.sorted())).put("onlySystem", view.onlySystem)
            .put("sort", view.sort).put("packages", JSONArray(view.packages.sorted()))) }
    }).toString()

    fun decode(text: String): List<SavedView> = try {
        val json = JSONObject(text)
        require(json.getInt("schema") == 1) { "Unsupported saved views version" }
        val views = json.getJSONArray("views")
        (0 until views.length()).map { index ->
            val entry = views.getJSONObject(index)
            fun strings(key: String): Set<String> = entry.getJSONArray(key).let { a ->
                (0 until a.length()).map { a.getString(it) }.toSet()
            }
            SavedView(entry.getString("id"), entry.getString("name"), if (entry.isNull("user")) null else entry.getInt("user"),
                entry.getString("query"), strings("filters"), entry.getBoolean("onlySystem"), entry.getString("sort"), strings("packages"))
                .also { require(it.id.isNotBlank() && it.name.isNotBlank()) }
        }.also { require(it.map { v -> v.id }.distinct().size == it.size) { "Duplicate saved view" } }
    } catch (e: Exception) { throw IllegalArgumentException("Cannot read saved views", e) }
}
