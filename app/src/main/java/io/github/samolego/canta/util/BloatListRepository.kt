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

    suspend fun load(forceRefresh: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            val settings = SettingsStore.getInstance()
            val snapshot = settings.bloatUpdateSettings()
            val url = snapshot.bloatListUrl.ifBlank { DEFAULT_BLOAT_URL }
            val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val cache = BloatListCache(File(context.filesDir, "uad-$key.json")) {
                context.assets.open("uad_lists.json").bufferedReader().use { it.readText() }
            }
            val current = cache.load()
            val sameUrl = snapshot.bloatCacheUrl == url
            val lastChecked = if (sameUrl) snapshot.bloatLastCheckedMs else 0
            val manager = context.getSystemService(ConnectivityManager::class.java)
            if (manager?.activeNetwork == null) return@withLock current.data
            val now = System.currentTimeMillis()
            if (!BloatUpdatePolicy.shouldFetch(
                    now, lastChecked, snapshot.autoUpdateBloatList, forceRefresh,
                    snapshot.bloatUnmeteredOnly, manager?.isActiveNetworkMetered != false,
                )) return@withLock current.data

            val etag = if (sameUrl) snapshot.bloatEtag else ""
            // Also throttle failed attempts; repeatedly opening the app must not retry every time.
            settings.setBloatCacheMetadata(url, etag, now)
            try {
                val updated = cache.refresh(BloatListClient(), url, etag)
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

    companion object {
        private val refreshLock = Mutex()
    }
}
