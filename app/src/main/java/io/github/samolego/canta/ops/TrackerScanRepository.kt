package io.github.samolego.canta.ops

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.samolego.canta.R
import io.github.samolego.canta.extension.getInfoForPackage
import io.github.samolego.canta.util.DexClassNames
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.TrackerCatalog
import io.github.samolego.canta.util.TrackerMatcher
import io.github.samolego.canta.util.TrackerRepository
import io.github.samolego.canta.util.TrackerScan
import io.github.samolego.canta.util.TrackerScanCache
import io.github.samolego.canta.util.apps.AppInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.TreeSet

data class TrackerScanProgress(val userId: Int, val done: Int, val total: Int)

/**
 * Scans installed APKs for Exodus tracker signatures the way εxodus does, from the class names the
 * DEX files define, and remembers the result per package and version. Results are per package, not
 * per profile, because every profile shares the same APK. Nothing leaves the device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackerScanRepository(context: Context, private val scope: CoroutineScope, private val trackers: TrackerRepository) {
    private val context = context.applicationContext
    private val file = File(context.filesDir, "tracker-scans.json")
    private val mutex = Mutex()
    private val scanDispatcher = Dispatchers.IO.limitedParallelism(2)
    private var matcher: TrackerMatcher? = null
    private var signatureKey = ""
    private var scanAllJob: Job? = null

    /** Package name → latest scan. Compose state, so tiles, filters and sorting update as scans finish. */
    val results = mutableStateMapOf<String, TrackerScan>()
    var progress by mutableStateOf<TrackerScanProgress?>(null)
        private set
    var catalog by mutableStateOf<TrackerCatalog?>(null)
        private set

    /** Matched tracker count for a package, or null without a successful scan. */
    fun count(packageName: String): Int? = results[packageName]?.takeIf { it.error == null }?.trackers?.size

    /** Loads signatures and the cached results once; [invalidate] forces a reload. */
    private suspend fun ensureLoaded(): TrackerMatcher = mutex.withLock {
        matcher?.let { return@withLock it }
        val loadedCatalog = trackers.load()
        val key = TrackerScanCache.signatureKey(loadedCatalog)
        val cached = withContext(Dispatchers.IO) {
            if (file.isFile) runCatching { TrackerScanCache.parse(file.readText(), key) }.getOrDefault(emptyMap()) else emptyMap()
        }
        val built = TrackerMatcher(loadedCatalog.signatures)
        withContext(Dispatchers.Main.immediate) {
            results.clear()
            results.putAll(cached)
            catalog = loadedCatalog
        }
        signatureKey = key
        matcher = built
        built
    }

    /** Forgets the loaded signatures, e.g. after the tracker source changed; cached results are re-keyed on the next scan. */
    fun invalidate() { matcher = null }

    suspend fun scan(app: AppInfo, force: Boolean = false): TrackerScan {
        val matcher = ensureLoaded()
        results[app.packageName]?.takeIf { !force && it.error == null && it.isFor(app.versionCode) }?.let { return it }
        val scan = withContext(scanDispatcher) { scanNow(app, matcher) }
        withContext(Dispatchers.Main.immediate) { results[app.packageName] = scan }
        persist()
        return scan
    }

    private fun scanNow(app: AppInfo, matcher: TrackerMatcher): TrackerScan {
        val now = System.currentTimeMillis()
        val info = app.applicationInfo ?: runCatching {
            context.packageManager.getInfoForPackage(app.packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)?.applicationInfo
        }.getOrNull()
        val apks = apkFiles(info)
        if (apks.isEmpty()) {
            return TrackerScan(app.packageName, app.versionCode, emptyList(), 0, 0, now, context.getString(R.string.tracker_no_apk))
        }
        return try {
            var classes = 0
            val found = TreeSet<String>()
            for (apk in apks) {
                val names = DexClassNames.fromApk(apk) { entry -> LogUtils.w(TAG, "${app.packageName}: skipped oversized $entry in ${apk.name}") }
                classes += names.size
                found.addAll(matcher.scan(names))
            }
            LogUtils.i(TAG, "${app.packageName}: ${found.size} tracker signatures in $classes classes (${apks.size} APK files)")
            TrackerScan(app.packageName, app.versionCode, found.toList(), classes, apks.size, now)
        } catch (e: Exception) {
            LogUtils.e(TAG, "${app.packageName}: tracker scan failed", e)
            TrackerScan(app.packageName, app.versionCode, emptyList(), 0, apks.size, now, e.message ?: e.toString())
        }
    }

    private fun apkFiles(info: ApplicationInfo?): List<File> =
        (listOfNotNull(info?.sourceDir) + info?.splitSourceDirs.orEmpty()).distinct().map(::File).filter { it.isFile && it.canRead() }

    /** Scans every installed app of one profile in the background; a scan already running is reused. */
    fun scanAll(apps: List<AppInfo>, userId: Int, onFinished: (scanned: Int, withTrackers: Int) -> Unit = { _, _ -> }): Job {
        scanAllJob?.takeIf { it.isActive }?.let { return it }
        val targets = apps.filter { !it.isUninstalled }
        val job = scope.launch {
            var done = 0
            var withTrackers = 0
            progress = TrackerScanProgress(userId, 0, targets.size)
            try {
                for (app in targets) {
                    ensureActive()
                    val scan = try { scan(app) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { LogUtils.e(TAG, "${app.packageName}: tracker scan failed", e); null }
                    if (scan != null && scan.error == null && scan.trackers.isNotEmpty()) withTrackers++
                    done++
                    progress = TrackerScanProgress(userId, done, targets.size)
                }
                LogUtils.i(TAG, "Tracker scan of user $userId finished: $withTrackers of $done apps match signatures")
                onFinished(done, withTrackers)
            } finally { progress = null }
        }
        scanAllJob = job
        return job
    }

    fun cancelScanAll() { scanAllJob?.cancel() }

    private suspend fun persist() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val text = TrackerScanCache.serialize(signatureKey, results.toMap())
            val temporary = File(file.path + ".tmp")
            try {
                temporary.writeText(text)
                check(temporary.renameTo(file)) { "Cannot save tracker scans" }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Could not persist tracker scans", e)
            } finally { temporary.delete() }
        }
    }

    private companion object { const val TAG = "TrackerScan" }
}
