package io.github.samolego.canta.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.samolego.canta.util.apps.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanaColorsTest {
    @Test fun everyRiskPairMeetsNormalTextContrast() {
        val palette = CanaColors.dracula()
        for ((name, pair) in listOf(
            "Recommended" to palette.recommended,
            "Advanced" to palette.advanced,
            "Expert" to palette.expert,
            "Unsafe" to palette.unsafe,
        )) {
            assertTextContrast(name, pair.container, pair.onContainer)
        }
    }

    @Test fun workTintDiffersFromPersonalAndOtherKindsUseSecondaryTint() {
        val palette = CanaColors.dracula()
        assertEquals(palette.personalTint, palette.profileTint(UserProfile.Kind.PERSONAL))
        assertEquals(palette.workTint, palette.profileTint(UserProfile.Kind.WORK))
        assertEquals(palette.otherTint, palette.profileTint(UserProfile.Kind.PRIVATE))
        assertEquals(palette.otherTint, palette.profileTint(UserProfile.Kind.CLONE))
        assertTrue(palette.personalTint != palette.workTint)
    }

    @Test fun materialTextRolesRemainReadableOnDraculaSurfaces() {
        with(DraculaColorScheme) {
            listOf(
                "Primary button" to (primary to onPrimary),
                "Secondary button" to (secondary to onSecondary),
                "Tertiary button" to (tertiary to onTertiary),
                "Personal profile" to (primaryContainer to onPrimaryContainer),
                "Other profile" to (secondaryContainer to onSecondaryContainer),
                "Work profile" to (tertiaryContainer to onTertiaryContainer),
                "Personal profile search label" to (primaryContainer to primary),
                "Other profile search label" to (secondaryContainer to primary),
                "Work profile search label" to (tertiaryContainer to primary),
                "Page" to (background to onBackground),
                "Surface" to (surface to onSurface),
                "Secondary text" to (surfaceVariant to onSurfaceVariant),
                "Dialog" to (surfaceContainerHigh to onSurface),
                "Snackbar" to (inverseSurface to inverseOnSurface),
                "Snackbar action" to (inverseSurface to inversePrimary),
                "Destructive button" to (error to onError),
                "Error banner" to (errorContainer to onErrorContainer),
                "Error banner nested error" to (errorContainer to error),
                "Error banner action" to (errorContainer to primary),
                "Error banner secondary action" to (errorContainer to secondary),
                "Warning banner action" to (CanaColors.dracula().advanced.container to primary),
                "Neutral banner action" to (secondaryContainer to primary),
                "Inline error" to (surface to error),
            ).forEach { (name, pair) -> assertTextContrast(name, pair.first, pair.second) }
        }
    }

    private fun assertTextContrast(name: String, background: Color, foreground: Color) {
        val contrast = contrastRatio(background, foreground)
        assertTrue("$name contrast is $contrast", contrast >= 4.5)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val light = maxOf(first.luminance(), second.luminance()).toDouble()
        val dark = minOf(first.luminance(), second.luminance()).toDouble()
        return (light + 0.05) / (dark + 0.05)
    }
}
