package io.github.samolego.canta.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.samolego.canta.util.RemovalRecommendation

fun RemovalRecommendation.riskIcon(): ImageVector = when (this) {
    RemovalRecommendation.RECOMMENDED -> Icons.Default.CheckCircle
    RemovalRecommendation.ADVANCED -> Icons.Default.Tune
    RemovalRecommendation.EXPERT -> Icons.Default.Warning
    RemovalRecommendation.UNSAFE -> Icons.Default.Dangerous
    RemovalRecommendation.SYSTEM -> Icons.Default.Android
}
