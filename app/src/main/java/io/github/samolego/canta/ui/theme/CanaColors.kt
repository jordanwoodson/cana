package io.github.samolego.canta.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.samolego.canta.util.apps.UserProfile

data class RiskColors(val container: Color, val onContainer: Color)

/** Semantic colors for risk, plus the current scheme's profile tints. */
data class CanaColors(
    val recommended: RiskColors,
    val advanced: RiskColors,
    val expert: RiskColors,
    val unsafe: RiskColors,
    val personalTint: Color,
    val workTint: Color,
    val otherTint: Color,
) {
    fun profileTint(kind: UserProfile.Kind): Color = when (kind) {
        UserProfile.Kind.PERSONAL -> personalTint
        UserProfile.Kind.WORK -> workTint
        else -> otherTint
    }

    companion object {
        fun light(
            personalTint: Color = Color(0xFF94F7BD),
            workTint: Color = Color(0xFFC0EAF0),
            otherTint: Color = Color(0xFFD1E8D7),
        ) = CanaColors(
            recommended = RiskColors(Color(0xFFC6F1D3), Color(0xFF00391D)),
            advanced = RiskColors(Color(0xFFFFE08D), Color(0xFF3B2D00)),
            expert = RiskColors(Color(0xFFFFDBCD), Color(0xFF5C1900)),
            unsafe = RiskColors(Color(0xFFBA1A1A), Color.White),
            personalTint = personalTint,
            workTint = workTint,
            otherTint = otherTint,
        )

        fun dark(
            personalTint: Color = Color(0xFF005234),
            workTint: Color = Color(0xFF1E4D52),
            otherTint: Color = Color(0xFF374B3E),
        ) = CanaColors(
            recommended = RiskColors(Color(0xFF1E5132), Color(0xFFC6F1D3)),
            advanced = RiskColors(Color(0xFF574400), Color(0xFFFFE08D)),
            expert = RiskColors(Color(0xFF7B2E0D), Color(0xFFFFDBCD)),
            unsafe = RiskColors(Color(0xFFFFB4AB), Color(0xFF690005)),
            personalTint = personalTint,
            workTint = workTint,
            otherTint = otherTint,
        )
    }
}

val LocalCanaColors = staticCompositionLocalOf { CanaColors.light() }
