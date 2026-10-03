package io.github.samolego.canta.util.apps

import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.RemovalRecommendation.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ShownRiskTest {
    private fun app(risk: RemovalRecommendation?, oldAction: String?) =
        AppInfo("app", "app", "1", 1, true, false, false, BloatData.fromJson(JSONObject().apply {
            put("removal", risk?.name)
            if (oldAction != null) put("verdict", JSONObject().put("action", oldAction))
        }))

    @Test fun upstreamRecommendationIsShownRegardlessOfLegacyClaudeVerdict() {
        assertEquals(RECOMMENDED, app(RECOMMENDED, "Uninstall").shownRisk)
        assertEquals(RECOMMENDED, app(RECOMMENDED, "Keep").shownRisk)
        assertEquals(RECOMMENDED, app(RECOMMENDED, null).shownRisk)
    }

    @Test fun everyUpstreamRatingAndUnclassifiedStateArePreserved() {
        for (risk in listOf(ADVANCED, EXPERT, UNSAFE)) assertEquals(risk, app(risk, "Keep").shownRisk)
        assertNull(app(null, "Uninstall").shownRisk)
    }
}
