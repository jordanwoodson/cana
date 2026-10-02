package io.github.samolego.canta.util.apps

import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.CanaVerdict
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.RemovalRecommendation.*
import org.junit.Assert.*
import org.junit.Test

class ShownRiskTest {
    private fun app(risk: RemovalRecommendation?, verdict: CanaVerdict?) =
        AppInfo("app", "app", "1", 1, true, false, false, BloatData(null, null, risk, verdict = verdict))

    @Test fun recommendedTagOnlyShowsWhenClaudeRecommendsUninstalling() {
        assertEquals(RECOMMENDED, app(RECOMMENDED, CanaVerdict(uninstall = true)).shownRisk)
        assertNull(app(RECOMMENDED, CanaVerdict(uninstall = false)).shownRisk)
        assertEquals(RECOMMENDED, app(RECOMMENDED, null).shownRisk)
    }

    @Test fun otherRatingsAndTheRealRatingAreUnchanged() {
        for (risk in listOf(ADVANCED, EXPERT, UNSAFE)) assertEquals(risk, app(risk, CanaVerdict(uninstall = false)).shownRisk)
        assertEquals(RECOMMENDED, app(RECOMMENDED, CanaVerdict(uninstall = false)).removalInfo)
    }
}
