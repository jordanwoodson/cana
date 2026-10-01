package io.github.samolego.canta.ops

import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.apps.AppInfo

data class ActionChoices(
    val primary: PackageAction,
    val reversible: List<PackageAction>,
    val removing: List<PackageAction>,
) {
    companion object {
        fun forSelection(apps: List<AppInfo>, removedTab: Boolean): ActionChoices {
            if (removedTab) return ActionChoices(PackageAction.REINSTALL, emptyList(),
                if (apps.any { it.isUpdatedSystemApp }) listOf(PackageAction.REMOVE_UPDATES) else emptyList())
            if (apps.isEmpty()) return ActionChoices(PackageAction.UNINSTALL, emptyList(), emptyList())

            val primary = when {
                apps.all { it.isDisabled } -> PackageAction.ENABLE
                apps.any { it.removalInfo in setOf(RemovalRecommendation.EXPERT, RemovalRecommendation.UNSAFE) } -> PackageAction.DISABLE
                else -> PackageAction.UNINSTALL
            }
            val reversible = buildList {
                if (apps.any { !it.isDisabled }) add(PackageAction.DISABLE)
                if (apps.any { it.isDisabled }) add(PackageAction.ENABLE)
                if (apps.any { !it.isSuspended }) add(PackageAction.SUSPEND)
                if (apps.any { it.isSuspended }) add(PackageAction.UNSUSPEND)
            }.filterNot { it == primary }
            val removing = listOf(PackageAction.UNINSTALL_KEEP_DATA, PackageAction.UNINSTALL).filterNot { it == primary }
            return ActionChoices(primary, reversible, removing)
        }
    }
}

enum class SelectionCheck { NONE, PARTIAL, ALL }

data class SelectionSummary(val total: Int, val hidden: Int, val visibleCheck: SelectionCheck) {
    companion object {
        fun of(selected: Set<String>, visible: Set<String>): SelectionSummary {
            val visibleSelected = selected.intersect(visible).size
            return SelectionSummary(selected.size, selected.size - visibleSelected, when {
                visibleSelected == 0 -> SelectionCheck.NONE
                visibleSelected == visible.size -> SelectionCheck.ALL
                else -> SelectionCheck.PARTIAL
            })
        }

        fun toggleVisible(selected: Set<String>, visible: Set<String>): Set<String> =
            if (visible.isNotEmpty() && selected.containsAll(visible)) selected - visible else selected + visible
    }
}
