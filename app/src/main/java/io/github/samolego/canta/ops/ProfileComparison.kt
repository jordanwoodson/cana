package io.github.samolego.canta.ops

import java.util.Locale

data class ComparisonPackage(
    val packageName: String,
    val name: String,
    val removed: Boolean = false,
    val disabled: Boolean = false,
    val suspended: Boolean = false,
)

/** Desired local choices; these values do not claim that Android currently enforces them. */
data class ComparisonSavedPrivacy(val blockNetwork: Boolean = false, val denyMetered: Boolean = false)

/** Null inventory or privacy means unavailable; empty collections mean a successful empty read. */
data class ComparisonProfileInventory(
    val userId: Int,
    val packages: List<ComparisonPackage>?,
    val savedPrivacy: Map<String, ComparisonSavedPrivacy>? = null,
)

enum class ComparisonPackageStatus { INSTALLED, REMOVED, ABSENT, UNAVAILABLE }

data class ProfileComparisonCell(
    val userId: Int,
    val status: ComparisonPackageStatus,
    val disabled: Boolean = false,
    val suspended: Boolean = false,
    val savedPrivacy: ComparisonSavedPrivacy? = null,
)

data class ProfileComparisonRow(val packageName: String, val name: String, val cells: List<ProfileComparisonCell>)

object ProfileComparison {
    fun rows(profiles: List<ComparisonProfileInventory>, query: String = ""): List<ProfileComparisonRow> {
        val inventories = profiles.distinctBy { it.userId }
        val packagesByUser = inventories.associate { profile -> profile.userId to profile.packages?.associateBy { it.packageName } }
        val names = inventories.flatMap { it.packages.orEmpty() }.groupBy { it.packageName }
        val packageNames = names.keys + inventories.flatMap { it.savedPrivacy.orEmpty().keys }
        val search = query.trim()
        return packageNames.asSequence().filter { packageName ->
            search.isEmpty() || packageName.contains(search, ignoreCase = true) ||
                names[packageName].orEmpty().any { it.name.contains(search, ignoreCase = true) }
        }.map { packageName ->
            val label = names[packageName]?.firstOrNull { it.name.isNotBlank() }?.name ?: packageName
            ProfileComparisonRow(packageName, label, inventories.map { profile ->
                val packages = packagesByUser[profile.userId]
                val app = packages?.get(packageName)
                val status = when {
                    packages == null -> ComparisonPackageStatus.UNAVAILABLE
                    app == null -> ComparisonPackageStatus.ABSENT
                    app.removed -> ComparisonPackageStatus.REMOVED
                    else -> ComparisonPackageStatus.INSTALLED
                }
                ProfileComparisonCell(profile.userId, status,
                    disabled = status == ComparisonPackageStatus.INSTALLED && app?.disabled == true,
                    suspended = status == ComparisonPackageStatus.INSTALLED && app?.suspended == true,
                    savedPrivacy = profile.savedPrivacy?.let { it[packageName] ?: ComparisonSavedPrivacy() },
                )
            })
        }.sortedWith(compareBy<ProfileComparisonRow> { it.name.lowercase(Locale.ROOT) }.thenBy { it.packageName }).toList()
    }
}
