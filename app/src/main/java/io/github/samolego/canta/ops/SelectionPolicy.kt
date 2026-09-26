package io.github.samolego.canta.ops

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
