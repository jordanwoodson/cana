package io.github.samolego.canta.ops

import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.LockdownSettings
import java.util.UUID

/** Null package sets mean that inventory could not be read, never an empty profile. */
data class PresetProfileInventory(
    val userId: Int,
    val name: String?,
    val kind: String?,
    val installedPackages: Set<String>? = null,
    val knownPackages: Set<String>? = installedPackages,
)

data class PresetProfilePreview(
    val userId: Int,
    val name: String?,
    val kind: String?,
    val inventoryAvailable: Boolean,
    val profileMismatch: Boolean,
    val profileKindUnknown: Boolean,
    val missingPackages: Set<String>,
    val alreadyRemovedPackages: Set<String>,
)

data class PresetPrivacyChange(val packageName: String, val before: LockdownSettings?, val after: LockdownSettings?)

data class PresetDiff(
    val addedRemovals: Set<String>,
    val removedRemovals: Set<String>,
    val privacyChanges: List<PresetPrivacyChange>,
    val profileKindChanged: Boolean,
)

/** The same immutable snapshot is rendered during review and passed to the atomic save. */
data class PresetImportReview(
    val preset: CantaPresetData,
    val previous: CantaPresetData?,
    val diff: PresetDiff,
    val profiles: List<PresetProfilePreview>,
)

object PresetPreview {
    fun reviewImport(preset: CantaPresetData, existing: List<CantaPresetData>, inventories: List<PresetProfileInventory>): PresetImportReview {
        val incoming = preset.snapshot().let { if (it.uuid.isBlank()) it.copy(uuid = UUID.randomUUID().toString()) else it }
        val previous = existing.firstOrNull { it.uuid == incoming.uuid }?.snapshot()
        val oldPrivacy = previous?.lockdown.orEmpty().associateBy { it.packageName }
        val newPrivacy = incoming.lockdown.associateBy { it.packageName }
        val diff = PresetDiff(
            addedRemovals = (incoming.apps - previous?.apps.orEmpty()).toSortedSet(),
            removedRemovals = (previous?.apps.orEmpty() - incoming.apps).toSortedSet(),
            privacyChanges = (oldPrivacy.keys + newPrivacy.keys).sorted().mapNotNull { name ->
                if (oldPrivacy[name] == newPrivacy[name]) null else PresetPrivacyChange(name, oldPrivacy[name], newPrivacy[name])
            },
            profileKindChanged = previous != null && previous.profileKind != incoming.profileKind,
        )
        return PresetImportReview(incoming, previous, diff, forProfiles(incoming, inventories))
    }

    /** Read-only input for import review and the final apply preflight. */
    fun forProfiles(preset: CantaPresetData, inventories: List<PresetProfileInventory>): List<PresetProfilePreview> {
        val privacyPackages = preset.lockdown.map { it.packageName }.toSet()
        return inventories.map { inventory ->
            val installed = inventory.installedPackages
            val known = inventory.knownPackages
            val available = installed != null && known != null
            PresetProfilePreview(
                userId = inventory.userId, name = inventory.name, kind = inventory.kind,
                inventoryAvailable = available,
                profileMismatch = preset.profileKind != null && inventory.kind != null && preset.profileKind != inventory.kind,
                profileKindUnknown = preset.profileKind != null && inventory.kind == null,
                missingPackages = if (available) ((preset.apps - known) + (privacyPackages - installed)).toSortedSet() else emptySet(),
                alreadyRemovedPackages = if (available) (preset.apps.intersect(known) - installed).toSortedSet() else emptySet(),
            )
        }
    }

    private fun CantaPresetData.snapshot() = copy(apps = apps.toSet(), lockdown = lockdown.toList())
}
