package io.github.samolego.canta.ops

/** Package and Android user travel together through navigation and external settings. */
data class AppTarget(val packageName: String, val userId: Int) {
    init {
        require(userId >= 0)
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")))
    }
    fun settingsCommand(): List<String> = listOf("am", "start", "--user", userId.toString(), "-a",
        "android.settings.APPLICATION_DETAILS_SETTINGS", "-d", "package:$packageName")
}
