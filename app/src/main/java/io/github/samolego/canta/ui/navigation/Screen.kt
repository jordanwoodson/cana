package io.github.samolego.canta.ui.navigation

sealed class Screen(val route: String) {
    data object AppDetail : Screen("app/{userId}/{packageName}") {
        fun path(packageName: String, userId: Int) = "app/$userId/${android.net.Uri.encode(packageName)}"
    }
    data object Privacy : Screen("privacy")
    data object Comparison : Screen("comparison")
    data object Main : Screen("main")
    data object Logs : Screen("logs")
    data object History : Screen("history")
    data object System : Screen("system")
    data object Settings : Screen("settings")
    data object Presets : Screen("presets")
}
