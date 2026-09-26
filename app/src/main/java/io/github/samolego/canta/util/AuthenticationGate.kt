package io.github.samolego.canta.util

/** The action closure retains the already-reviewed packages, profiles and approvals. */
suspend fun <T> authenticatedAction(required: Boolean, authenticate: suspend () -> Boolean, action: suspend () -> T): T? =
    if (!required || authenticate()) action() else null
