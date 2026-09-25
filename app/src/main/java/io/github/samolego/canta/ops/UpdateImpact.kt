package io.github.samolego.canta.ops

import io.github.samolego.canta.util.apps.UserProfile

data class UpdateImpact(
    val packageName: String,
    val userId: Int,
    val updated: Boolean,
    val installed: Boolean,
    val sizeBytes: Long,
    val otherInstalledProfiles: List<UserProfile>,
    val error: String? = null,
) {
    val reclaimableBytes: Long get() = if (otherInstalledProfiles.isEmpty()) sizeBytes else 0
}
