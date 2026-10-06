package io.github.samolego.canta.util

/**
 * The Metro Motorola Edge 2025 review served by tools/debloat-server over the tailnet: the Android
 * Debloat List with Cana's audited ratings merged over it. The same review is bundled in the APK as
 * the offline fallback, so a phone off the tailnet still gets the device ratings.
 */
const val DEFAULT_BLOAT_URL =
    "https://ubuntu-server.taila70e29.ts.net:3090/devices/moto-edge-2025/uad_lists.json"

/** The upstream Android Debloat List archive that the bundled copy and the server build from. */
const val ANDROID_DEBLOAT_LIST_URL =
    "https://codeload.github.com/MuntashirAkon/android-debloat-list/zip/refs/heads/master"

object BloatListSource {
    private val legacyUrls = setOf(
        "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json",
        "https://ubuntu-server.taila70e29.ts.net:3090/uad_lists.json",
        ANDROID_DEBLOAT_LIST_URL,
    )

    /** Switch existing Cana installations to the current default while preserving other custom lists. */
    fun effectiveUrl(savedUrl: String): String = savedUrl.trim().let {
        if (it.isEmpty() || it in legacyUrls) DEFAULT_BLOAT_URL else it
    }
}
