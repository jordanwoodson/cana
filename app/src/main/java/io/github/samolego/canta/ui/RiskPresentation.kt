package io.github.samolego.canta.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.ui.theme.LocalCanaColors
import io.github.samolego.canta.ui.theme.RiskColors

fun RemovalRecommendation.riskIcon(): ImageVector = when (this) {
    RemovalRecommendation.RECOMMENDED -> Icons.Default.CheckCircle
    RemovalRecommendation.ADVANCED -> Icons.Default.Tune
    RemovalRecommendation.EXPERT -> Icons.Default.Warning
    RemovalRecommendation.UNSAFE -> Icons.Default.Dangerous
    RemovalRecommendation.SYSTEM -> Icons.Default.Android
}

@Composable
fun RemovalRecommendation.riskColors(): RiskColors {
    val palette = LocalCanaColors.current
    return when (this) {
        RemovalRecommendation.RECOMMENDED -> palette.recommended
        RemovalRecommendation.ADVANCED -> palette.advanced
        RemovalRecommendation.EXPERT -> palette.expert
        RemovalRecommendation.UNSAFE -> palette.unsafe
        RemovalRecommendation.SYSTEM -> RiskColors(
            MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
