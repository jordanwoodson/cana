package io.github.samolego.canta.util

import org.junit.Assert.*
import org.junit.Test

class BloatListSourceTest {
    @Test fun switchesLegacyDefaultsAndTheOldCanaServerToAndroidDebloatList() {
        for (url in listOf("", "  ",
            "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json",
            "https://ubuntu-server.taila70e29.ts.net:3090/uad_lists.json")) {
            assertEquals(DEFAULT_BLOAT_URL, BloatListSource.effectiveUrl(url))
        }
        assertTrue(DEFAULT_BLOAT_URL.contains("MuntashirAkon/android-debloat-list"))
    }

    @Test fun preservesOtherCustomListUrls() {
        val url = "https://example.com/custom.json"
        assertEquals(url, BloatListSource.effectiveUrl(url))
        assertEquals(DEFAULT_BLOAT_URL, BloatListSource.effectiveUrl(DEFAULT_BLOAT_URL))
    }
}
