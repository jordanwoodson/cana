package io.github.samolego.canta.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.savedViewsStore by preferencesDataStore("saved_views")
data class SavedViewsState(val views: List<SavedView> = emptyList(), val loading: Boolean = false, val unavailable: Boolean = false)

class SavedViewStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.savedViewsStore)
    private val key = stringPreferencesKey("views")
    val state = store.data.map { prefs ->
        try { SavedViewsState(prefs[key]?.let(SavedViewJson::decode).orEmpty()) }
        catch (_: Exception) { SavedViewsState(unavailable = true) }
    }.catch { emit(SavedViewsState(unavailable = true)) }
    suspend fun save(view: SavedView) {
        require(view.name.isNotBlank())
        store.edit { prefs ->
            // Keep malformed data intact for recovery; never replace it with an empty collection.
            val existing = prefs[key]?.let(SavedViewJson::decode).orEmpty()
            prefs[key] = SavedViewJson.encode(existing.filterNot { it.id == view.id } + view)
        }
    }
    suspend fun delete(id: String) {
        store.edit { prefs -> prefs[key] = SavedViewJson.encode(prefs[key]?.let(SavedViewJson::decode).orEmpty().filterNot { it.id == id }) }
    }
}
