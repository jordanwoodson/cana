package io.github.samolego.canta.util

const val DEFAULT_BLOAT_URL =
    "https://codeload.github.com/MuntashirAkon/android-debloat-list/zip/refs/heads/master"

object BloatListSource {
    private val legacyUrls = setOf(
        "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json",
        "https://ubuntu-server.taila70e29.ts.net:3090/uad_lists.json",
    )

    /** Switch existing Cana installations to the new source while preserving other custom lists. */
    fun effectiveUrl(savedUrl: String): String = savedUrl.trim().let {
        if (it.isEmpty() || it in legacyUrls) DEFAULT_BLOAT_URL else it
    }
}
