package io.github.samolego.canta.util

/** Canta's default list: Universal Android Debloater Next Generation's uad_lists.json. */
const val DEFAULT_BLOAT_URL =
    "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"

/** The upstream Android Debloat List archive that the bundled offline copy is built from. */
const val ANDROID_DEBLOAT_LIST_URL =
    "https://codeload.github.com/MuntashirAkon/android-debloat-list/zip/refs/heads/master"

object BloatListSource {
    /** Earlier Cana defaults: the retired tailnet debloat server and the Android Debloat List archive. */
    private val legacyUrls = setOf(
        "https://ubuntu-server.taila70e29.ts.net:3090/uad_lists.json",
        "https://ubuntu-server.taila70e29.ts.net:3090/devices/moto-edge-2025/uad_lists.json",
        ANDROID_DEBLOAT_LIST_URL,
    )

    /** Switch existing Cana installations to the current default while preserving other custom lists. */
    fun effectiveUrl(savedUrl: String): String = savedUrl.trim().let {
        if (it.isEmpty() || it in legacyUrls) DEFAULT_BLOAT_URL else it
    }
}
