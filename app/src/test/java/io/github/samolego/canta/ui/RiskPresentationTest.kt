package io.github.samolego.canta.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import io.github.samolego.canta.util.RemovalRecommendation
import org.junit.Assert.assertEquals
import org.junit.Test

class RiskPresentationTest {
    @Test fun recommendationIconsExplainRiskRatherThanSuggestRemoval() {
        assertEquals(Icons.Default.CheckCircle.name, RemovalRecommendation.RECOMMENDED.riskIcon().name)
        assertEquals(Icons.Default.Tune.name, RemovalRecommendation.ADVANCED.riskIcon().name)
        assertEquals(Icons.Default.Warning.name, RemovalRecommendation.EXPERT.riskIcon().name)
        assertEquals(Icons.Default.Dangerous.name, RemovalRecommendation.UNSAFE.riskIcon().name)
    }
}
