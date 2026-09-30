package io.github.samolego.canta.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.samolego.canta.util.apps.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanaColorsTest {
    @Test fun everyRiskPairMeetsNormalTextContrastInBothThemes() {
        for ((theme, palette) in listOf("light" to CanaColors.light(), "dark" to CanaColors.dark())) {
            for ((name, pair) in listOf(
                "Recommended" to palette.recommended,
                "Advanced" to palette.advanced,
                "Expert" to palette.expert,
                "Unsafe" to palette.unsafe,
            )) {
                val contrast = contrastRatio(pair.container, pair.onContainer)
                assertTrue("$theme $name contrast is $contrast", contrast >= 4.5)
            }
        }
    }

    @Test fun workTintDiffersFromPersonalAndOtherKindsUseSecondaryTint() {
        val palette = CanaColors.light()
        assertEquals(palette.personalTint, palette.profileTint(UserProfile.Kind.PERSONAL))
        assertEquals(palette.workTint, palette.profileTint(UserProfile.Kind.WORK))
        assertEquals(palette.otherTint, palette.profileTint(UserProfile.Kind.PRIVATE))
        assertEquals(palette.otherTint, palette.profileTint(UserProfile.Kind.CLONE))
        assertTrue(palette.personalTint != palette.workTint)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val light = maxOf(first.luminance(), second.luminance()).toDouble()
        val dark = minOf(first.luminance(), second.luminance()).toDouble()
        return (light + 0.05) / (dark + 0.05)
    }
}
