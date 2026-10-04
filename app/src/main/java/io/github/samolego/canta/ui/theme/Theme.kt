package io.github.samolego.canta.ui.theme

import android.app.Activity
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal val DraculaColorScheme = darkColorScheme(
    primary = DraculaPurple,
    onPrimary = DraculaBackground,
    primaryContainer = DraculaPersonalContainer,
    onPrimaryContainer = DraculaForeground,
    secondary = DraculaPink,
    onSecondary = DraculaBackground,
    secondaryContainer = DraculaOtherContainer,
    onSecondaryContainer = DraculaForeground,
    tertiary = DraculaCyan,
    onTertiary = DraculaBackground,
    tertiaryContainer = DraculaWorkContainer,
    onTertiaryContainer = DraculaForeground,
    background = DraculaBackground,
    onBackground = DraculaForeground,
    surface = DraculaBackground,
    onSurface = DraculaForeground,
    surfaceVariant = DraculaSelection,
    onSurfaceVariant = DraculaSecondaryText,
    surfaceContainer = DraculaSurface,
    surfaceContainerLow = DraculaSurfaceLow,
    surfaceContainerLowest = DraculaDeepBackground,
    surfaceContainerHigh = DraculaSurfaceHigh,
    surfaceContainerHighest = DraculaSelection,
    surfaceDim = DraculaDeepBackground,
    surfaceBright = DraculaSelection,
    surfaceTint = DraculaBackground,
    inverseSurface = DraculaSelection,
    inverseOnSurface = DraculaForeground,
    inversePrimary = DraculaCyan,
    outline = DraculaOutline,
    outlineVariant = DraculaSelection,
    error = DraculaError,
    onError = DraculaBackground,
    // Error banners can contain red inline errors and purple action buttons.
    errorContainer = DraculaDeepBackground,
    onErrorContainer = DraculaError,
    scrim = DraculaDeepBackground,
)

/** Cana always uses Dracula, including when Android itself is in light mode. */
@Composable
fun CantaTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(LocalCanaColors provides CanaColors.dracula()) {
        MaterialTheme(colorScheme = DraculaColorScheme, typography = Typography, shapes = CanaShapes) {
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = DraculaPurple,
                    backgroundColor = DraculaSelection,
                ),
                content = content,
            )
        }
    }
}
