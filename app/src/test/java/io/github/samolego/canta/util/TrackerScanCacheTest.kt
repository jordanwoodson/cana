package io.github.samolego.canta.util

import org.junit.Assert.*
import org.junit.Test

class TrackerScanCacheTest {
    private val scans = mapOf(
        "com.example.clean" to TrackerScan("com.example.clean", 7, emptyList(), 1200, 1, 1_700_000_000_000),
        "com.example.tracked" to TrackerScan("com.example.tracked", 42, listOf("Alpha", "Beta"), 30_000, 3, 1_700_000_000_001),
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
        assertTrue(scan.isFor(42))
        assertFalse(scan.isFor(43))
    }
}
