package io.github.samolego.canta.ops

import io.github.samolego.canta.util.TrackerSignatures
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TrackerSignaturesTest {
    @Test fun parsesExodusAlternativesAndMatchesLiteralNamespaces() {
        val signatures = TrackerSignatures.parse(JSONObject("""{"trackers":{"1":{"name":"Example","code_signature":"com.example.analytics.|other/sdk/"}}}"""))
        assertEquals(listOf("Example"), TrackerSignatures.matches("com.example.analytics.TrackerService", signatures))
        assertEquals(listOf("Example"), TrackerSignatures.matches("other.sdk.Receiver", signatures))
        assertTrue(TrackerSignatures.matches("comXexampleXanalyticsXTrackerService", signatures).isEmpty())
        assertTrue(TrackerSignatures.matches("innocent.com.example.analytics.TrackerService", signatures).isEmpty())
    }

    @Test fun emptySignaturesDoNotMatchEveryComponentAndMalformedListsFail() {
        val signatures = TrackerSignatures.parse(JSONObject("""{"trackers":{"1":{"name":"Empty","code_signature":""}}}"""))
        assertTrue(TrackerSignatures.matches("any.Component", signatures).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { TrackerSignatures.parse(JSONObject("{}")) }
    }

    @Test fun fullAttributedSnapshotContainsFirebaseMatches() {
        val parsed = TrackerSignatures.parse(JSONObject(File("src/main/assets/exodus-trackers.json").readText()))
        assertTrue(parsed.size >= 400)
        assertTrue(TrackerSignatures.matches("com.google.android.gms.measurement.AppMeasurementReceiver", parsed).isNotEmpty())
        assertTrue(TrackerSignatures.matches("com.vendor.sizmek.TrackingService", parsed).contains("Sizmek"))
    }
}
