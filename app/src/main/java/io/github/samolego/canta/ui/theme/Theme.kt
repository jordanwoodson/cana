package io.github.samolego.canta.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CanaMint,
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFF94F7BD),
    secondary = Color(0xFFB5CCBB),
    onSecondary = Color(0xFF213528),
    secondaryContainer = Color(0xFF374B3E),
    onSecondaryContainer = Color(0xFFD1E8D7),
    tertiary = Color(0xFFA4CED3),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF1E4D52),
    onTertiaryContainer = Color(0xFFC0EAF0),
    background = Color(0xFF101512),
    onBackground = Color(0xFFE0E4DF),
    surface = Color(0xFF101512),
    onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF404942),
    onSurfaceVariant = Color(0xFFC0C9C0),
    surfaceContainer = CanaCharcoal,
    surfaceContainerLow = Color(0xFF181D19),
    surfaceContainerLowest = Color(0xFF0B100D),
    surfaceContainerHigh = Color(0xFF262C27),
    surfaceContainerHighest = Color(0xFF313731),
    surfaceTint = CanaMint,
    inverseSurface = Color(0xFFE0E4DF),
    inverseOnSurface = Color(0xFF2D322E),
    inversePrimary = CanaForest,
    outline = Color(0xFF8A938B),
    outlineVariant = Color(0xFF404942),
)

private val LightColorScheme = lightColorScheme(
    primary = CanaForest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF94F7BD),
    onPrimaryContainer = Color(0xFF002111),
    secondary = Color(0xFF4F6355),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8D7),
    onSecondaryContainer = Color(0xFF0C1F14),
    tertiary = Color(0xFF38656A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC0EAF0),
    onTertiaryContainer = Color(0xFF002023),
    background = Color(0xFFF6FBF4),
    onBackground = CanaCharcoal,
    surface = Color(0xFFF6FBF4),
    onSurface = CanaCharcoal,
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF404942),
    surfaceContainer = Color(0xFFEBF0E9),
    surfaceContainerLow = Color(0xFFF1F6EF),
    surfaceContainerLowest = Color.White,
    surfaceContainerHigh = Color(0xFFE5EAE3),
    surfaceContainerHighest = Color(0xFFDFE4DD),
    surfaceTint = CanaForest,
    inverseSurface = Color(0xFF2D322E),
    inverseOnSurface = Color(0xFFEFF3EC),
    inversePrimary = CanaMint,
    outline = Color(0xFF707970),
    outlineVariant = Color(0xFFC0C9C0),
)

@Composable
fun CantaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
