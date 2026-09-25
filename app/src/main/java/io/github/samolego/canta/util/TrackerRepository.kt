package io.github.samolego.canta.util

import android.content.Context
import io.github.samolego.canta.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class TrackerCatalog(val signatures: List<TrackerSignature>, val source: String, val error: String? = null)

class TrackerRepository(context: Context, private val fetch: (String) -> JSONObject = { url ->
    checkNotNull(BloatListClient().fetch(url, "").data) { "Server returned no tracker data" }
}) {
    private val context = context.applicationContext
    private val mutex = Mutex()
    private val settings get() = SettingsStore.getInstance()
    private val bundled by lazy { TrackerSignatures.parse(JSONObject(context.assets.open("exodus-trackers.json").bufferedReader().use { it.readText() })) }
    private fun cache(url: String) = File(context.filesDir, "trackers-" +
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".json")

    suspend fun load(): TrackerCatalog = withContext(Dispatchers.IO) { mutex.withLock {
        val url = settings.trackerListUrlFlow.first()
        if (url.isBlank()) TrackerCatalog(bundled, "Exodus Privacy")
        else try { TrackerCatalog(TrackerSignatures.parse(JSONObject(cache(url).readText())), url) }
        catch (e: Exception) { TrackerCatalog(bundled, "Exodus Privacy", e.message) }
    } }

    /** A custom source is opt-in and fetched only when saved/refreshed, never from Exodus by default. */
    suspend fun setSource(input: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val url = input.trim()
        if (url.isNotEmpty()) {
            val json = fetch(url)
            TrackerSignatures.parse(json)
            val target = cache(url)
            val temporary = File(target.path + ".tmp")
            try {
                temporary.writeText(json.toString())
                check(temporary.renameTo(target)) { "Cannot save tracker cache" }
            } finally { temporary.delete() }
        }
        settings.setTrackerListUrl(url)
    } }
}
