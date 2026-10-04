package io.github.samolego.canta.ops

import io.github.samolego.canta.util.apps.UserProfile

/** Raised only before a mutation is dispatched. Cleanup must not start fallback or repair. */
internal class PackageMutationRefused(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

internal object ProfileIsolation {
    fun blockingProfiles(userId: Int, profiles: List<UserProfile>, installedIn: Set<Int>): List<UserProfile> {
        require(userId >= 0 && profiles.any { it.id == userId }) { "Selected profile no longer exists" }
        return profiles.filter { it.id != userId && it.parentId == userId &&
            it.id in installedIn && it.deleteAppWithParent != false }
    }

    fun canCleanUpdates(userId: Int, installed: Map<Int, Boolean>): Boolean =
        installed.containsKey(userId) && installed.values.none { it }

    /** Binder hides this property even from shell; dumpsys exposes the canonical user policy.
     * Only consume each user's indented block, never defaults under the User types section.
     * Missing, malformed, or repeated values remain unknown (and block cascading removal).
     */
    fun parseDeleteWithParent(dump: String): Map<Int, Boolean> {
        val values = mutableMapOf<Int, MutableList<Boolean>>()
        var user: Int? = null
        val header = Regex("^  UserInfo\\{(\\d+):.*")
        val property = Regex("^\\s+mDeleteAppWithParent=(true|false)\\s*$")
        val seen = mutableSetOf<Int>()
        val duplicates = mutableSetOf<Int>()
        dump.lineSequence().forEach { line ->
            val match = header.matchEntire(line)
            if (match != null) {
                user = match.groupValues[1].toIntOrNull()
                user?.let { if (!seen.add(it)) duplicates += it }
            } else if (line.isNotBlank() && !line.startsWith("    ")) {
                user = null
            } else {
                property.matchEntire(line)?.let { prop ->
                    user?.let { values.getOrPut(it) { mutableListOf() } += prop.groupValues[1].toBooleanStrict() }
                }
            }
        }
        return values.filter { (id, flags) -> id !in duplicates && flags.size == 1 }.mapValues { it.value.single() }
    }
}
