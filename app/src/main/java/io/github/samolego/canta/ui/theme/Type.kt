package io.github.samolego.canta.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

private val defaults = Typography()

// Terminal character in headings and controls; proportional text keeps long descriptions readable.
val Typography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = FontFamily.Monospace),
    displayMedium = defaults.displayMedium.copy(fontFamily = FontFamily.Monospace),
    displaySmall = defaults.displaySmall.copy(fontFamily = FontFamily.Monospace),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = FontFamily.Monospace),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = FontFamily.Monospace),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = FontFamily.Monospace),
    titleLarge = defaults.titleLarge.copy(fontFamily = FontFamily.Monospace),
    titleMedium = defaults.titleMedium.copy(fontFamily = FontFamily.Monospace),
    titleSmall = defaults.titleSmall.copy(fontFamily = FontFamily.Monospace),
    labelLarge = defaults.labelLarge.copy(fontFamily = FontFamily.Monospace),
    labelMedium = defaults.labelMedium.copy(fontFamily = FontFamily.Monospace),
    labelSmall = defaults.labelSmall.copy(fontFamily = FontFamily.Monospace),
)
