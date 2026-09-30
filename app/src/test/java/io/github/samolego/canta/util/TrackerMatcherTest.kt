package io.github.samolego.canta.util

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TrackerMatcherTest {
    private val signatures = TrackerSignatures.parse(JSONObject("""{"trackers":{
        "1":{"name":"Alpha","code_signature":"com.alpha.sdk.|com.alpha.core"},
        "2":{"name":"Beta","code_signature":".beta.tracking."},
        "3":{"name":"Gamma","code_signature":"io.g"},
        "4":{"name":"Delta","code_signature":"com/alpha/sdk/legacy/"},
        "5":{"name":"Epsilon","code_signature":""}}}"""))
    private val names = listOf("com.alpha.sdk.Client", "com.alpha.sdk.legacy.Old", "com.alpha.core", "com.alpha.coreX", "com.alpha.other.Foo",
        "org.beta.tracking.T", "x.beta.trackingless", "io.gadget.App", "io.g", "Standalone", "com.Alpha", "io", "com.alpha", "com.alpha.", "", ".")

    @Test fun agreesWithTheReferenceMatcher() {
        val matcher = TrackerMatcher(signatures)
        for (name in names) assertEquals(name, TrackerSignatures.matches(name, signatures), matcher.matches(name))
        assertEquals(listOf("Alpha", "Delta"), matcher.matches("com.alpha.sdk.legacy.Old"))
        assertEquals(listOf("Beta"), matcher.matches("org.beta.tracking.T"))
        assertEquals(listOf("Gamma"), matcher.matches("io.gadget.App"))
        assertTrue(matcher.matches("com.alpha.other.Foo").isEmpty())
    }

    @Test fun scanCollectsDistinctSortedNames() {
        assertEquals(listOf("Alpha", "Beta", "Delta", "Gamma"), TrackerMatcher(signatures).scan(names))
        assertEquals(emptyList<String>(), TrackerMatcher(signatures).scan(listOf("nothing.Here")))
        assertEquals(emptyList<String>(), TrackerMatcher(emptyList()).scan(names))
    }

    @Test fun agreesWithTheReferenceOnTheBundledSnapshot() {
        val bundled = TrackerSignatures.parse(JSONObject(File("src/main/assets/exodus-trackers.json").readText()))
        val matcher = TrackerMatcher(bundled)
        val probes = listOf("com.google.android.gms.measurement.AppMeasurementReceiver", "com.facebook.appevents.AppEventsLogger",
            "io.branch.referral.Branch", "com.appsflyer.AppsFlyerLib", "androidx.core.app.ComponentActivity", "com.example.Main", "kotlin.Unit") +
            bundled.flatMap { it.prefixes }.flatMap { listOf(it, it + "X", it.trimEnd('.') + ".Inner\$Class", "pre." + it) }
        for (name in probes) assertEquals(name, TrackerSignatures.matches(name, bundled), matcher.matches(name))
        assertTrue(matcher.matches("com.google.android.gms.measurement.AppMeasurementReceiver").isNotEmpty())
        assertTrue(matcher.matches("androidx.core.app.ComponentActivity").isEmpty())
    }
}
