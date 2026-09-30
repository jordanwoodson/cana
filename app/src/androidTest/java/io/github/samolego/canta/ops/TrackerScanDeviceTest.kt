package io.github.samolego.canta.ops

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.util.*
import io.github.samolego.canta.util.apps.ApkSnapshot
import io.github.samolego.canta.util.apps.AppInfo
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TrackerScanDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val oldCatalog = TrackerCatalog(listOf(TrackerSignature("Old", listOf("old.tracker."))), "old")
    private val newCatalog = TrackerCatalog(listOf(TrackerSignature("New", listOf("new.tracker."))), "new")

    @Test fun missingSplitFailsAndBatchSummaryDoesNotCountItAsScanned() = runBlocking {
        fixture { dir, scope, app ->
            val split = apk(dir.resolve("split.apk"))
            app.applicationInfo!!.splitSourceDirs = arrayOf(split.path)
            val target = app.copy(apkIdentity = ApkSnapshot.read(app.applicationInfo.sourceDir, listOf(split.path))!!.identity)
            assertTrue(split.delete())
            val repository = TrackerScanRepository(context, scope, { oldCatalog }, file = dir.resolve("scans.json"))
            assertNotNull(repository.scan(target).error)
            assertNull(repository.count(target))
            val outcome = CompletableDeferred<List<Int>>()
            withContext(Dispatchers.Main.immediate) {
                repository.scanAll(listOf(target), 0) { scanned, matched, failed ->
                    outcome.complete(listOf(scanned, matched, failed))
                }
            }.join()
            assertEquals(listOf(0, 0, 1), outcome.await())
        }
    }

    @Test fun successfulCountsRejectDifferentVersionsAndApkFiles() = runBlocking {
        fixture { dir, scope, app ->
            val repository = TrackerScanRepository(context, scope, { oldCatalog }, file = dir.resolve("scans.json"))
            assertNull(repository.scan(app).error)
            assertEquals(0, repository.count(app))
            assertNull(repository.count(app.copy(versionCode = 2)))
            assertNull(repository.count(app.copy(apkIdentity = "f".repeat(64))))
            assertNull(repository.count(app.copy(apkIdentity = "")))
            val loaded = TrackerScanCache.parse(dir.resolve("scans.json").readText(), TrackerScanCache.signatureKey(oldCatalog))
            assertEquals(app.apkIdentity, loaded.getValue(app.packageName).apkIdentity)
        }
    }

    @Test fun sourceChangedDuringLoadRetriesBeforePublishing() = runBlocking {
        fixture { dir, scope, app ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var loads = 0
            val repository = TrackerScanRepository(context, scope, {
                if (++loads == 1) { entered.complete(Unit); release.await(); oldCatalog } else newCatalog
            }, file = dir.resolve("scans.json"))
            val task = async { repository.scan(app) }
            entered.await()
            withContext(Dispatchers.Main.immediate) { repository.invalidate() }
            release.complete(Unit)
            assertNull(task.await().error)
            assertEquals("new", repository.catalog?.source)
            assertEquals(2, loads)
        }
    }

    @Test fun oldScanCannotPublishAfterSourceInvalidation() = runBlocking {
        fixture { dir, scope, app ->
            val entered = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            var current = oldCatalog
            val repository = TrackerScanRepository(context, scope, { current }, readClasses = { _, checkActive, emit ->
                entered.complete(Unit)
                check(release.await(30, TimeUnit.SECONDS))
                checkActive()
                emit("old.tracker.Class")
            }, file = dir.resolve("scans.json"))
            try {
                val task = async { repository.scan(app) }
                entered.await()
                withContext(Dispatchers.Main.immediate) {
                    current = newCatalog
                    repository.invalidate()
                    assertTrue(repository.results.isEmpty())
                }
                release.countDown()
                try { task.await(); fail("Old scan must be discarded") }
                catch (_: CancellationException) { }
                assertTrue(repository.results.isEmpty())
                assertEquals(emptyList<String>(), repository.scan(app).trackers)
                val cache = TrackerScanCache.parse(dir.resolve("scans.json").readText(), TrackerScanCache.signatureKey(newCatalog))
                assertEquals(emptyList<String>(), cache.getValue(app.packageName).trackers)
            } finally { release.countDown() }
        }
    }

    @Test fun cancelledBatchCannotHideReplacementProgress() = runBlocking {
        fixture { dir, scope, app ->
            val firstEntered = CompletableDeferred<Unit>()
            val secondEntered = CompletableDeferred<Unit>()
            val releaseFirst = CountDownLatch(1)
            val releaseSecond = CountDownLatch(1)
            val reads = AtomicInteger()
            val repository = TrackerScanRepository(context, scope, { oldCatalog }, readClasses = { _, checkActive, _ ->
                if (reads.incrementAndGet() == 1) {
                    firstEntered.complete(Unit)
                    check(releaseFirst.await(30, TimeUnit.SECONDS))
                } else {
                    secondEntered.complete(Unit)
                    check(releaseSecond.await(30, TimeUnit.SECONDS))
                }
                checkActive()
            }, file = dir.resolve("scans.json"))
            try {
                val first = withContext(Dispatchers.Main.immediate) { repository.scanAll(listOf(app), 0) }
                firstEntered.await()
                val replacement = withContext(Dispatchers.Main.immediate) {
                    repository.cancelScanAll()
                    repository.scanAll(listOf(app), 10)
                }
                releaseFirst.countDown()
                secondEntered.await()
                first.join()
                withContext(Dispatchers.Main.immediate) {
                    assertEquals(TrackerScanProgress(10, 0, 1), repository.progress)
                }
                releaseSecond.countDown()
                replacement.join()
                withContext(Dispatchers.Main.immediate) { assertNull(repository.progress) }
            } finally { releaseFirst.countDown(); releaseSecond.countDown() }
        }
    }

    private fun apk(file: File): File = file.apply {
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("resources.arsc")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
    }

    private suspend fun fixture(block: suspend (File, CoroutineScope, AppInfo) -> Unit) {
        val dir = File(context.cacheDir, "tracker-test-${UUID.randomUUID()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val base = apk(dir.resolve("base.apk"))
            val info = ApplicationInfo().apply { sourceDir = base.path }
            val app = AppInfo("Test", "test.app", "1", 1, false, false, false, null,
                applicationInfo = info, apkIdentity = ApkSnapshot.read(base.path, emptyList())!!.identity)
            withTimeout(60000) { block(dir, scope, app) }
        } finally { scope.cancel(); scope.coroutineContext[Job]!!.join(); dir.deleteRecursively() }
    }
}
