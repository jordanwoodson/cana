package io.github.samolego.canta.util.apps

import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.InstallData
import io.github.samolego.canta.R
import java.util.Locale

/**
 * Filter for the app list.
 * @param name Name of the filter.
 * @param shouldShow Function to determine if the app should be shown.
 */
class Filter(
    val name: String,
    val shouldShow: (AppInfo) -> Boolean,
    val removalRecommendation: RemovalRecommendation? = null,
    val nameRes: Int? = null,
    val id: String = name.lowercase(Locale.ROOT).replace(' ', '_'),
) {
    val group: FilterGroup get() = when {
        removalRecommendation != null -> FilterGroup.RISK
        id.startsWith("category_") -> FilterGroup.CATEGORY
        else -> FilterGroup.STATE
    }
    companion object {
        /**
         * Filter to show all apps.
         */
        val any: Filter = Filter(name = "Any", shouldShow = { true }, nameRes = R.string.filter_any)

        val user = Filter(name = "User", shouldShow = { app -> !app.isSystemApp }, nameRes = R.string.filter_user)
        val leftoverUpdates = Filter("Leftover updates", { it.isUninstalled && it.isUpdatedSystemApp },
            nameRes = R.string.leftover_updates)
        val unused = Filter("Unused for 90 days", { !it.isUninstalled && io.github.samolego.canta.ops.ManagementPolicy.unused90Days(it.lastUsed, it.firstInstallTime, System.currentTimeMillis()) }, nameRes = R.string.unused_90_days)

        /**
         * List of available filters.
         */
        val availableFilters: List<Filter>

        init {
            // Filters are generated from the RemovalRecommendation enum.
            val removalFilters =
                RemovalRecommendation.entries.filter { RemovalRecommendation.SYSTEM != it }
                    .map { entry ->
                        Filter(
                            name = entry.toString().lowercase(Locale.ROOT)
                                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                            shouldShow = { app -> app.removalInfo == entry },
                            removalRecommendation = entry
                            , nameRes = when (entry) {
                                RemovalRecommendation.RECOMMENDED -> R.string.risk_recommended
                                RemovalRecommendation.ADVANCED -> R.string.risk_advanced
                                RemovalRecommendation.EXPERT -> R.string.risk_expert
                                RemovalRecommendation.UNSAFE -> R.string.risk_unsafe
                                else -> R.string.filter_any
                            }, id = "risk_${entry.name.lowercase(Locale.ROOT)}"
                        )
                    }.toMutableList()
            removalFilters.add(0, any)

            // Apps that are not system apps.
            removalFilters.add(1, user)

            val unclassified =
                Filter(name = "Unclassified", shouldShow = { app -> app.removalInfo == null }, nameRes = R.string.filter_unclassified)
            removalFilters.add(2, unclassified)

            // Apps that are disabled
            val disabled = Filter(name = "Disabled", shouldShow = { app -> app.isDisabled }, nameRes = R.string.filter_disabled)
            removalFilters.add(3, disabled)
            removalFilters.add(4, leftoverUpdates)
            removalFilters.add(5, unused)

            val categoryNames = mapOf(
                InstallData.GOOGLE to R.string.category_google,
                InstallData.OEM to R.string.category_oem,
                InstallData.CARRIER to R.string.category_carrier,
                InstallData.AOSP to R.string.category_aosp,
                InstallData.MISC to R.string.category_misc,
            )
            categoryNames.forEach { (category, label) ->
                removalFilters.add(Filter(
                    name = category.name,
                    nameRes = label,
                    shouldShow = { app -> app.bloatData?.installData == category },
                    id = "category_${category.name.lowercase(Locale.ROOT)}",
                ))
            }

            availableFilters = removalFilters
        }
        fun fromId(id: String): Filter? = availableFilters.find { it.id == id }
    }
}

enum class FilterGroup { STATE, RISK, CATEGORY }
