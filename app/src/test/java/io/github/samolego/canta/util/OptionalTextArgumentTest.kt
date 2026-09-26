package io.github.samolego.canta.util

import org.junit.Assert.assertEquals
import org.junit.Test

class OptionalTextArgumentTest {
    @Test fun legacyTranslationsWithMissingOrMalformedPlaceholdersRemainReadable() {
        assertEquals("Thanks to UAD", optionalTextArgument("Thanks to %s", "UAD"))
        assertEquals("Tap 3 more times", optionalTextArgument("Tap %1\$d more times", 3))
        assertEquals("No argument here", optionalTextArgument("No argument here", 3))
        assertEquals("برای فعال کردن همه قابلیت ها ، به d% بیشتر ضربه بزنید.",
            optionalTextArgument("برای فعال کردن همه قابلیت ها ، به d% بیشتر ضربه بزنید.", 3))
        assertEquals("100% complete", optionalTextArgument("100% complete", "UAD"))
    }
}
