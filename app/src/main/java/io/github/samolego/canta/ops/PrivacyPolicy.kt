package io.github.samolego.canta.ops

object PrivacyPolicy {
    fun uid(userId: Int, appId: Int): Int {
        require(userId >= 0 && appId in 10_000 until 100_000) { "Invalid app/profile UID" }
        return (userId.toLong() * 100_000 + appId).also { require(it <= Int.MAX_VALUE) }.toInt()
    }
    fun appOpMode(output: String): String {
        val lines = output.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        // A missing RUN_ANY_IN_BACKGROUND entry uses MODE_ALLOWED on older Android.
        // Newer versions print the operation's default mode explicitly; MODE_DEFAULT is
        // a different value, so preserving the reported mode matters when undoing a change.
        if (lines == listOf("No operations.")) return "allow"
        if (lines.size == 2 && lines.first() == "No operations.") {
            return Regex("Default mode: (allow|ignore|deny|default|foreground)").matchEntire(lines[1])
                ?.groupValues?.get(1) ?: error("Unrecognized background app-op state")
        }
        // A uid-level override takes precedence over the package mode printed below it.
        return Regex("RUN_ANY_IN_BACKGROUND: (allow|ignore|deny|default|foreground)\\b")
            .find(output)?.groupValues?.get(1) ?: error("Unrecognized background app-op state")
    }
    fun uidList(output: String): Set<Int> {
        require(Regex("(?i)^Restrict background (blacklisted|whitelisted) UIDs:").containsMatchIn(output.trim()))
        val values = output.substringAfter(':').trim()
        if (values == "none" || values.isEmpty()) return emptySet()
        return values.split(Regex("\\s+")).map { it.toInt() }.toSet()
    }
    fun mutablePermission(flags: Int): Boolean = flags and (4 or 16) == 0
}
