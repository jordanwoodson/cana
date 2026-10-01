package io.github.samolego.canta.util.apps

data class ScopeFilters(val onlySystem: Boolean, val filterIds: Set<String>)

/** Maps the scope control onto the existing saved-view fields and filter IDs. */
enum class AppScope {
    ALL, SYSTEM, USER;

    fun applyTo(filterIds: Set<String>): ScopeFilters = ScopeFilters(
        onlySystem = this == SYSTEM,
        filterIds = filterIds - Filter.user.id + if (this == USER) setOf(Filter.user.id) else emptySet(),
    )

    companion object {
        fun from(onlySystem: Boolean, filterIds: Set<String>): AppScope = when {
            Filter.user.id in filterIds -> USER
            onlySystem -> SYSTEM
            else -> ALL
        }
    }
}
