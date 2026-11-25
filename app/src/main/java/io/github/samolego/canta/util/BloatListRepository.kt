package io.github.samolego.canta.util

import android.content.Context
import android.net.ConnectivityManager
import io.github.samolego.canta.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Serializes list refreshes across profile switches and uses a separate cache for each URL. */
class BloatListRepository(context: Context) {
    private val context = context.applicationContext

    private val guidanceLock = Mutex()
    private var lastSource: Pair<String, Long>? = null
    private var lastGuidance = emptyMap<String, BloatData>()

    private fun cacheFile(url: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "uad-$key.json")
    }

    /** A completed refresh invalidates parsed guidance for the next safety check. */
    suspend fun guidance(configuredUrl: String): Map<String, BloatData> = withContext(Dispatchers.IO) {
        guidanceLock.withLock {
            val url = BloatListSource.effectiveUrl(configuredUrl)
            val source = url to generation.get()
            if (source != lastSource) {
                val json = BloatListCache(cacheFile(url), ::bundledList).load().data
                lastGuidance = buildMap {
                    for (name in json.keys()) json.optJSONObject(name)?.let { put(name, BloatData.fromJson(it)) }
                }
                lastSource = source
            }
            lastGuidance
        }
    }

    suspend fun cached(): JSONObject = withContext(Dispatchers.IO) {
        val url = BloatListSource.effectiveUrl(SettingsStore.getInstance().snapshot().bloatListUrl)
        BloatListCache(cacheFile(url), ::bundledList).load().data
    }

    suspend fun load(forceRefresh: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            val settings = SettingsStore.getInstance()
            val snapshot = settings.snapshot()
            val url = BloatListSource.effectiveUrl(snapshot.bloatListUrl)
            val cache = BloatListCache(cacheFile(url), ::bundledList)
            val current = cache.load()
            val sameUrl = snapshot.bloatCacheUrl == url
            val lastChecked = if (sameUrl) snapshot.bloatLastCheckedMs else 0
            val manager = context.getSystemService(ConnectivityManager::class.java)
            if (manager?.activeNetwork == null) return@withLock current.data
            val now = System.currentTimeMillis()
            if (!BloatUpdatePolicy.shouldFetch(
                    now, lastChecked, snapshot.autoUpdateBloatList, forceRefresh,
                    snapshot.bloatUnmeteredOnly, manager.isActiveNetworkMetered,
                )) return@withLock current.data

            val etag = if (sameUrl) snapshot.bloatEtag else ""
            // Also throttle failed attempts; repeatedly opening the app must not retry every time.
            settings.setBloatCacheMetadata(url, etag, now)
            try {
                val updated = cache.refresh(BloatListClient(), url, etag)
                generation.incrementAndGet()
                settings.setBloatCacheMetadata(url, updated.etag, now)
                LogUtils.i("BloatList", "List checked using conditional GET: $url")
                updated.data
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.e("BloatList", "Could not update list; using cached or bundled data", e)
                current.data
            }
        }
    }

    /** The offline fallback until the first download: the bundled Android Debloat List. */
    private fun bundledList(): String = context.assets.open("android-debloat-list.zip").use {
        parseAndroidDebloatArchive(it.readBloatBytes())
    }.toString()

    companion object {
        private val refreshLock = Mutex()
        private val generation = java.util.concurrent.atomic.AtomicLong()
    }
}
