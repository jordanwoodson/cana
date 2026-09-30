package io.github.samolego.canta.ops

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.MainThread
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
import io.github.samolego.canta.util.TrackerScan
import io.github.samolego.canta.util.TrackerScanCache
import io.github.samolego.canta.util.apps.ApkSnapshot
import io.github.samolego.canta.util.apps.AppInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.TreeSet

data class TrackerScanProgress(val userId: Int, val done: Int, val total: Int)

/** APK code is shared by profiles. Cached scans are tied to its file metadata and signature set. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackerScanRepository(context: Context, private val scope: CoroutineScope,
    private val loadCatalog: suspend () -> TrackerCatalog,
    private val readClasses: (File, () -> Unit, (String) -> Unit) -> Unit = DexClassNames::forEachClass,
    private val file: File = File(context.filesDir, "tracker-scans.json"),
) {
    private val context = context.applicationContext
    private val loadMutex = Mutex()
    private val fileMutex = Mutex()
    private val scanDispatcher = Dispatchers.IO.limitedParallelism(1)
    private data class Loaded(val generation: Long, val key: String, val matcher: TrackerMatcher)
    private var loaded: Loaded? = null
    private var scanAllJob: Job? = null

    /** Published only on Main; invalidating a source also removes its visible counts immediately. */
    val results = mutableStateMapOf<String, TrackerScan>()
    var generation by mutableStateOf(0L)
        private set
    var progress by mutableStateOf<TrackerScanProgress?>(null)
        private set
    var catalog by mutableStateOf<TrackerCatalog?>(null)
        private set

    fun result(app: AppInfo): TrackerScan? = results[app.packageName]
        ?.takeIf { it.isFor(app.versionCode, app.apkIdentity) }
    fun count(app: AppInfo): Int? = result(app)?.takeIf { it.error == null }?.trackers?.size

    private suspend fun ensureLoaded(): Loaded = loadMutex.withLock {
        while (true) {
            loaded?.let { return@withLock it }
            val revision = generation
            val source = loadCatalog()
            val key = TrackerScanCache.signatureKey(source)
            val cached = fileMutex.withLock { withContext(Dispatchers.IO) {
                // A cache is expendable. Never load an unbounded or malformed file into memory.
                if (file.isFile && file.length() <= MAX_CACHE_BYTES)
                    runCatching { TrackerScanCache.parse(file.readText(), key) }.getOrDefault(emptyMap())
                else emptyMap()
            } }
            currentCoroutineContext().ensureActive()
            if (revision != generation) continue
            val ready = Loaded(revision, key, TrackerMatcher(source.signatures))
            results.clear()
            results.putAll(cached)
            catalog = source
            loaded = ready
            return@withLock ready
        }
        @Suppress("UNREACHABLE_CODE") error("Unreachable")
    }

    @MainThread
    fun invalidate() {
        generation++
        loaded = null
        results.clear()
        catalog = null
        cancelScanAll()
    }

    suspend fun scan(app: AppInfo, force: Boolean = false): TrackerScan = withContext(Dispatchers.Main.immediate) {
        scanLoaded(app, force).also { persist() }
    }

    private suspend fun scanLoaded(app: AppInfo, force: Boolean = false): TrackerScan {
        val source = ensureLoaded()
        result(app)?.takeIf { !force && it.error == null }?.let { return it }
        val scan = withContext(scanDispatcher) {
            val jobContext = currentCoroutineContext()
            scanNow(app, source.matcher) { jobContext.ensureActive() }
        }
        // A source can change while APK parsing runs on IO. Old work must never repopulate it.
        if (source.generation != generation) throw CancellationException("Tracker source changed")
        results[app.packageName] = scan
        return scan
    }

    private fun scanNow(app: AppInfo, matcher: TrackerMatcher, checkActive: () -> Unit): TrackerScan {
        val now = System.currentTimeMillis()
        var apkCount = 0
        return try {
            val info = app.applicationInfo ?: context.packageManager
                .getInfoForPackage(app.packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)?.applicationInfo
            val snapshot = ApkSnapshot.read(info?.sourceDir, info?.splitSourceDirs?.toList().orEmpty())
                ?: error(context.getString(R.string.tracker_no_apk))
            apkCount = snapshot.files.size
            check(app.apkIdentity.isNotEmpty() && snapshot.identity == app.apkIdentity) {
                context.getString(R.string.tracker_apk_changed)
            }
            var classes = 0
            val found = TreeSet<String>()
            for (apk in snapshot.files) {
                readClasses(apk, checkActive) { name ->
                    classes++
                    found.addAll(matcher.matches(name))
                }
            }
            check(ApkSnapshot.read(info?.sourceDir, info?.splitSourceDirs?.toList().orEmpty())?.identity == snapshot.identity) {
                context.getString(R.string.tracker_apk_changed)
            }
            LogUtils.i(TAG, "${app.packageName}: ${found.size} tracker signatures in $classes classes ($apkCount APK files)")
            TrackerScan(app.packageName, app.versionCode, found.toList(), classes, apkCount, now, apkIdentity = snapshot.identity)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            LogUtils.e(TAG, "${app.packageName}: tracker scan failed", e)
            TrackerScan(app.packageName, app.versionCode, emptyList(), 0, apkCount, now,
                e.message ?: e.toString(), app.apkIdentity)
        }
    }

    /** Application scope owns the scan. Successful scans and failures are reported separately. */
    @MainThread
    fun scanAll(apps: List<AppInfo>, userId: Int,
        onFinished: (scanned: Int, withTrackers: Int, failed: Int) -> Unit = { _, _, _ -> },
    ): Job {
        scanAllJob?.takeIf { it.isActive }?.let { return it }
        val targets = apps.filter { !it.isUninstalled }
        // Assign ownership before starting: Main.immediate can otherwise finish inline.
        val job = scope.launch(Dispatchers.Main.immediate, start = CoroutineStart.LAZY) {
            var done = 0
            var withTrackers = 0
            var failed = 0
            progress = TrackerScanProgress(userId, 0, targets.size)
            try {
                for (app in targets) {
                    ensureActive()
                    val scan = try { scanLoaded(app) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { LogUtils.e(TAG, "${app.packageName}: tracker scan failed", e); null }
                    if (scan == null || scan.error != null) failed++
                    else if (scan.trackers.isNotEmpty()) withTrackers++
                    done++
                    progress = TrackerScanProgress(userId, done, targets.size)
                    // Avoid rewriting the entire result map for every app in a large inventory.
                    if (done % 20 == 0) persist()
                }
                onFinished(done - failed, withTrackers, failed)
            } finally {
                try { withContext(NonCancellable) { persist() } }
                finally {
                    // A cancelled job may finish after its replacement has already started.
                    if (scanAllJob === currentCoroutineContext()[Job]) {
                        progress = null
                        scanAllJob = null
                    }
                }
            }
        }
        scanAllJob = job
        job.start()
        return job
    }

    fun cancelScanAll() { scanAllJob?.cancel() }

    /** Capture the result map and its catalog key together on Main before writing on IO. */
    private suspend fun persist() {
        val source = loaded ?: return
        val snapshot = results.toMap()
        fileMutex.withLock { withContext(Dispatchers.IO) {
            val temporary = File(file.path + ".tmp")
            try {
                temporary.writeText(TrackerScanCache.serialize(source.key, snapshot))
                check(temporary.length() <= MAX_CACHE_BYTES) { "Tracker cache is too large" }
                check(temporary.renameTo(file)) { "Cannot save tracker scans" }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { LogUtils.e(TAG, "Could not persist tracker scans", e) }
            finally { temporary.delete() }
        } }
    }

    private companion object {
        const val TAG = "TrackerScan"
        const val MAX_CACHE_BYTES = 16L * 1024 * 1024
    }
}
