package io.github.samolego.canta.util

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceReviewTest {
    private val upstream = JSONObject("""{
        "com.example.browser": {"list":"Google","removal":"Advanced","description":"Upstream text.",
            "source":"Android Debloat List (MuntashirAkon)","label":"Stock Browser","warning":"Install a replacement first.",
            "web":["https://example.com/report"],"suggestions":"browsers",
            "alternatives":[{"id":"org.example.browser","label":"Open Browser","repo":"https://example.com/browser"}],
            "dependencies":["android"],"neededBy":["com.example.client"],"labels":["tracking"]},
        "com.example.keep": {"list":"Aosp","removal":"Unsafe","description":"Core.","source":"Android Debloat List (MuntashirAkon)",
            "dependencies":[],"neededBy":[],"labels":[]}
    }""")

    private fun review(vararg entries: Pair<String, String>) = JSONObject(entries.joinToString(prefix = "{", postfix = "}") { "\"${it.first}\":${it.second}" })

    private val reviewedBrowser = """{"list":"Google","removal":"Recommended","description":"Audit text.\nRemoving breaks: nothing.",
        "source":"Cana Motorola Edge 2025 audit","dependencies":[],"neededBy":[],"labels":["exodus-signature-match"],
        "verdict":{"action":"Uninstall","benefits":["privacy"],"reason":"Recommend removal: duplicate."}}"""

    @Test fun reviewDecidesRatingAndTextWhileUpstreamKeepsNamesAndReplacements() {
        val merged = applyDeviceReview(upstream, review("com.example.browser" to reviewedBrowser))
        val entry = merged.getJSONObject("com.example.browser")
        val data = BloatData.fromJson(entry)
        assertEquals(RemovalRecommendation.RECOMMENDED, data.removal)
        assertTrue(data.description!!.startsWith("Audit text."))
        assertEquals("Cana Motorola Edge 2025 audit", data.source)
        assertEquals("Stock Browser", data.label)
        assertEquals("Install a replacement first.", data.warning)
        assertEquals(listOf("https://example.com/report"), data.web)
        assertEquals("Open Browser", data.alternatives.single().label)
        assertEquals(listOf("exodus-signature-match"), data.labels)
        assertTrue(data.dependencies.isEmpty())
        assertEquals("Uninstall", entry.getJSONObject("verdict").getString("action"))
        // Untouched upstream packages and the original object survive.
        assertEquals("Unsafe", merged.getJSONObject("com.example.keep").getString("removal"))
        assertEquals("Advanced", upstream.getJSONObject("com.example.browser").getString("removal"))
    }

    @Test fun addsPackagesOnlyTheReviewKnows() {
        val merged = applyDeviceReview(upstream, review("com.example.userapp" to """{"list":"Misc","removal":"Advanced",
            "description":"User app.\nRemoving breaks: it.","source":"Cana Motorola Edge 2025 audit",
            "dependencies":[],"neededBy":[],"labels":[]}"""))
        assertEquals(3, merged.length())
        val data = BloatData.fromJson(merged.getJSONObject("com.example.userapp"))
        assertEquals(RemovalRecommendation.ADVANCED, data.removal)
        assertEquals(InstallData.MISC, data.installData)
        assertNull(data.label)
    }

    @Test fun rejectsMalformedReviews() {
        val bad = listOf(
            "{}",
            """{"com.example.browser":"text"}""",
            """{"bad id":{"list":"Misc","removal":"Advanced","description":"d","source":"s","dependencies":[],"neededBy":[],"labels":[]}}""",
            """{"com.example.a":{"list":"Other","removal":"Advanced","description":"d","source":"s","dependencies":[],"neededBy":[],"labels":[]}}""",
            """{"com.example.a":{"list":"Misc","removal":"delete","description":"d","source":"s","dependencies":[],"neededBy":[],"labels":[]}}""",
            """{"com.example.a":{"list":"Misc","removal":"Advanced","description":"","source":"s","dependencies":[],"neededBy":[],"labels":[]}}""",
            """{"com.example.a":{"list":"Misc","removal":"Advanced","description":"d","source":"s","dependencies":"x","neededBy":[],"labels":[]}}""",
            """{"com.example.a":{"list":"Misc","removal":"Advanced","description":"d","source":"s","dependencies":[""],"neededBy":[],"labels":[]}}""",
            """{"com.example.a":{"list":"Misc","removal":"Advanced","description":"d","source":"s","dependencies":[],"neededBy":[],"labels":[],"verdict":{"action":"Maybe","benefits":[],"reason":"r"}}}""",
        )
        for (text in bad) {
            assertThrows(text, Exception::class.java) { applyDeviceReview(upstream, JSONObject(text)) }
        }
    }
}
