package io.github.samolego.canta.util.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppScopeTest {
    @Test fun mapsExistingScopeState() {
        assertEquals(AppScope.ALL, AppScope.from(false, emptySet()))
        assertEquals(AppScope.SYSTEM, AppScope.from(true, emptySet()))
        assertEquals(AppScope.USER, AppScope.from(false, setOf(Filter.user.id)))
        assertEquals(AppScope.USER, AppScope.from(true, setOf(Filter.user.id)))
    }

    @Test fun userAndDisabledCanCoexist() {
        val disabled = Filter.availableFilters.first { it.name == "Disabled" }.id
        val selected = AppScope.USER.applyTo(setOf(disabled))
        assertEquals(setOf(disabled, Filter.user.id), selected.filterIds)
        assertEquals(false, selected.onlySystem)
        assertEquals(AppScope.USER, AppScope.from(selected.onlySystem, selected.filterIds))
        assertEquals(setOf(disabled), AppScope.SYSTEM.applyTo(selected.filterIds).filterIds)
    }
}
