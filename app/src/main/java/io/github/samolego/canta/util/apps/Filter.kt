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
        this == user -> FilterGroup.SCOPE
        removalRecommendation != null -> FilterGroup.RISK
        id.startsWith("privacy_") -> FilterGroup.PRIVACY
        id.startsWith("category_") -> FilterGroup.CATEGORY
        id.startsWith("verdict_") -> FilterGroup.VERDICT
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
        /** Apps whose APK code matched at least one tracker signature in a Cana scan. */
        val trackers = Filter("With trackers", { app -> (io.github.samolego.canta.ops.CanaServices.getInstance().trackerScans.count(app) ?: 0) > 0 },
            nameRes = R.string.filter_trackers, id = "privacy_trackers")
        /** Cana's keep-or-uninstall advice for privacy and performance debloating. */
        val verdictUninstall = Filter("Cana: uninstall", { it.bloatData?.verdict?.uninstall == true },
            nameRes = R.string.filter_verdict_uninstall, id = "verdict_uninstall")
        val verdictKeep = Filter("Cana: keep", { it.bloatData?.verdict?.uninstall == false },
            nameRes = R.string.filter_verdict_keep, id = "verdict_keep")
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
            removalFilters.add(6, trackers)

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

            removalFilters.add(verdictUninstall)
            removalFilters.add(verdictKeep)

            availableFilters = removalFilters
        }
        fun fromId(id: String): Filter? = availableFilters.find { it.id == id }
    }
}

enum class FilterGroup { SCOPE, STATE, RISK, VERDICT, CATEGORY, PRIVACY }
