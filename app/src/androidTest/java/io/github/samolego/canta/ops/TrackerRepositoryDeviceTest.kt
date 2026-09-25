package io.github.samolego.canta.ops

import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.data.SettingsStore
import io.github.samolego.canta.util.TrackerRepository
import io.github.samolego.canta.util.TrackerSignatures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class TrackerRepositoryDeviceTest {
    @Test fun customSourceIsValidatedBeforeSaveAndSurvivesOfflineReload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsStore.getInstance()
        val original = settings.trackerListUrlFlow.first()
        val url = "https://example.invalid/cana-test-trackers.json"
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val cache = File(context.filesDir, "trackers-$hash.json")
        try {
            val repository = TrackerRepository(context) { JSONObject("""{"trackers":{"1":{"name":"Test tracker","code_signature":"test.tracker."}}}""") }
            repository.setSource(url)
            assertEquals(url, settings.trackerListUrlFlow.first())
            val offline = TrackerRepository(context) { error("Network must not be used while loading") }.load()
            assertEquals(url, offline.source)
            assertEquals(listOf("Test tracker"), TrackerSignatures.matches("test.tracker.Service", offline.signatures))
            try {
                TrackerRepository(context) { JSONObject("{}") }.setSource("https://example.invalid/broken.json")
                fail("Invalid source must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertEquals(url, settings.trackerListUrlFlow.first())
            cache.writeText("corrupt")
            val fallback = repository.load()
            assertEquals("Exodus Privacy", fallback.source)
            assertNotNull(fallback.error)
            assertTrue(fallback.signatures.size >= 400)
        } finally {
            settings.setTrackerListUrl(original)
            cache.delete()
        }
    }
}
