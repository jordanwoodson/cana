package io.github.samolego.canta.util

import org.junit.Assert.*
import org.junit.Test

class BloatListSourceTest {
    @Test fun defaultsToCantasUadList() {
        assertEquals("https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json", DEFAULT_BLOAT_URL)
        assertTrue(ANDROID_DEBLOAT_LIST_URL.contains("MuntashirAkon/android-debloat-list"))
    }

    @Test fun switchesEveryEarlierDefaultToCantasList() {
        for (url in listOf("", "  ",
            "https://ubuntu-server.taila70e29.ts.net:3090/uad_lists.json",
            "https://ubuntu-server.taila70e29.ts.net:3090/devices/moto-edge-2025/uad_lists.json",
            "https://codeload.github.com/MuntashirAkon/android-debloat-list/zip/refs/heads/master")) {
            assertEquals(DEFAULT_BLOAT_URL, BloatListSource.effectiveUrl(url))
        }
    }

    @Test fun preservesOtherCustomListUrls() {
        val url = "https://example.com/custom.json"
        assertEquals(url, BloatListSource.effectiveUrl(url))
        assertEquals(DEFAULT_BLOAT_URL, BloatListSource.effectiveUrl(DEFAULT_BLOAT_URL))
    }
}
