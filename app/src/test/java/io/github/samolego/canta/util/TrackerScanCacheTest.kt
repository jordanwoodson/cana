package io.github.samolego.canta.util

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class TrackerScanCacheTest {
    private val scans = mapOf(
        "com.example.clean" to TrackerScan("com.example.clean", 7, emptyList(), 1200, 1, 1_700_000_000_000, apkIdentity = "a".repeat(64)),
        "com.example.tracked" to TrackerScan("com.example.tracked", 42, listOf("Alpha", "Beta"), 30_000, 3, 1_700_000_000_001, apkIdentity = "b".repeat(64)),
        "com.example.broken" to TrackerScan("com.example.broken", 1, emptyList(), 0, 0, 1_700_000_000_002, "APK unreadable"),
    )

    @Test fun roundTripsEveryField() {
        val text = TrackerScanCache.serialize("key-1", scans)
        assertEquals(scans, TrackerScanCache.parse(text, "key-1"))
    }

    @Test fun resultsFromAnotherSignatureSetOrBrokenFilesAreDropped() {
        val text = TrackerScanCache.serialize("key-1", scans)
        assertTrue(TrackerScanCache.parse(text, "key-2").isEmpty())
        assertTrue(TrackerScanCache.parse("{broken", "key-1").isEmpty())
        assertTrue(TrackerScanCache.parse("", "key-1").isEmpty())
        assertTrue(TrackerScanCache.parse("""{"version":99,"signatures":"key-1","scans":{}}""", "key-1").isEmpty())
        assertTrue(TrackerScanCache.parse("""{"version":1,"signatures":"key-1","scans":{"a.b":"not an object"}}""", "key-1").isEmpty())
    }

    @Test fun incompleteRecordsCannotBecomeSuccessfulZeroTrackerScans() {
        val json = JSONObject(TrackerScanCache.serialize("key-1", scans))
        val clean = json.getJSONObject("scans").getJSONObject("com.example.clean")
        for (field in listOf("trackers", "versionCode", "classes", "apks", "scannedAt")) {
            val broken = JSONObject(json.toString())
            broken.getJSONObject("scans").getJSONObject("com.example.clean").remove(field)
            assertFalse("Missing $field", TrackerScanCache.parse(broken.toString(), "key-1").containsKey("com.example.clean"))
        }
        clean.put("apks", -1)
        assertFalse(TrackerScanCache.parse(json.toString(), "key-1").containsKey("com.example.clean"))
    }

    @Test fun signatureKeyIgnoresOrderButNotContent() {
        val a = TrackerSignature("Alpha", listOf("com.alpha.", "com.alpha.core"))
        val b = TrackerSignature("Beta", listOf(".beta."))
        val forward = TrackerScanCache.signatureKey(TrackerCatalog(listOf(a, b), "x"))
        val reversed = TrackerScanCache.signatureKey(TrackerCatalog(listOf(b, TrackerSignature("Alpha", listOf("com.alpha.core", "com.alpha."))), "y"))
        assertEquals(forward, reversed)
        assertEquals(24, forward.length)
        assertNotEquals(forward, TrackerScanCache.signatureKey(TrackerCatalog(listOf(a), "x")))
        assertNotEquals(forward, TrackerScanCache.signatureKey(TrackerCatalog(listOf(a, TrackerSignature("Beta", listOf(".beta.", ".gamma."))), "x")))
    }

    @Test fun staleVersionsAreDetectable() {
        val scan = scans.getValue("com.example.tracked")
        assertTrue(scan.isFor(42, "b".repeat(64)))
        assertFalse(scan.isFor(43, "b".repeat(64)))
        assertFalse(scan.isFor(42, "c".repeat(64)))
        assertFalse(scan.isFor(42, ""))
    }
}
