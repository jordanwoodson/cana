package io.github.samolego.canta.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.samolego.canta.util.apps.UserProfile

data class RiskColors(val container: Color, val onContainer: Color)

/** Fixed Dracula risk colors and profile tints, independent of Android's wallpaper. */
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
        fun dracula() = CanaColors(
            recommended = RiskColors(DraculaSelection, DraculaGreen),
            // Warning banners also contain purple text-button actions.
            advanced = RiskColors(DraculaDeepBackground, DraculaYellow),
            expert = RiskColors(DraculaSelection, DraculaOrange),
            unsafe = RiskColors(DraculaRed, DraculaBackground),
            personalTint = DraculaPersonalContainer,
            workTint = DraculaWorkContainer,
            otherTint = DraculaOtherContainer,
        )
    }
}

val LocalCanaColors = staticCompositionLocalOf { CanaColors.dracula() }
