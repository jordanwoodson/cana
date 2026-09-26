package io.github.samolego.canta.util

import java.util.Locale

/** Legacy Crowdin text may omit its single argument or contain literal percent signs. */
fun optionalTextArgument(text: String, argument: Any): String =
    Regex("%(?:1\\$)?([sd])").replace(text) { match ->
        if (match.groupValues[1] == "d" && argument is Int)
            String.format(Locale.getDefault(), "%d", argument)
        else argument.toString()
    }
