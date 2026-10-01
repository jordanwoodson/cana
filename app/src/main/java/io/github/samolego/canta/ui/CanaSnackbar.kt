package io.github.samolego.canta.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

val LocalCanaSnackbar = staticCompositionLocalOf<SnackbarHostState> {
    error("Cana snackbar host is only available inside CantaApp")
}
