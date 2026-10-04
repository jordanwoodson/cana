package io.github.samolego.canta.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.samolego.canta.R

internal val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val defaults = Typography()

// Bundle the same typeface for every role, including descriptions and controls.
val Typography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = JetBrainsMono),
    displayMedium = defaults.displayMedium.copy(fontFamily = JetBrainsMono),
    displaySmall = defaults.displaySmall.copy(fontFamily = JetBrainsMono),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold),
    titleLarge = defaults.titleLarge.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold),
    titleMedium = defaults.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold),
    titleSmall = defaults.titleSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = JetBrainsMono),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = JetBrainsMono),
    bodySmall = defaults.bodySmall.copy(fontFamily = JetBrainsMono),
    labelLarge = defaults.labelLarge.copy(fontFamily = JetBrainsMono),
    labelMedium = defaults.labelMedium.copy(fontFamily = JetBrainsMono),
    labelSmall = defaults.labelSmall.copy(fontFamily = JetBrainsMono),
)
