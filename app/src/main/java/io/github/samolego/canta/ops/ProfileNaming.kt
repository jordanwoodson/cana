package io.github.samolego.canta.ops

import io.github.samolego.canta.util.apps.UserProfile

/** Pure naming decision; the UI supplies translated labels. */
object ProfileNaming {
    data class Labels(
        val personal: String,
        val work: String,
        val clone: String,
        val privateProfile: String,
        val user: String,
        val other: String,
        val fallbackUser: (Int) -> String,
    )

    fun label(kind: UserProfile.Kind?, name: String?, id: Int, currentUserId: Int, labels: Labels): String =
        when (kind) {
            UserProfile.Kind.PERSONAL -> labels.personal
            UserProfile.Kind.WORK -> labels.work
            UserProfile.Kind.CLONE -> labels.clone
            UserProfile.Kind.PRIVATE -> labels.privateProfile
            UserProfile.Kind.USER -> name?.takeIf { it.isNotBlank() } ?: labels.user
            UserProfile.Kind.OTHER -> name?.takeIf { it.isNotBlank() } ?: labels.other
            null -> if (id == 0 && currentUserId == 0) labels.personal else labels.fallbackUser(id)
        }
}
