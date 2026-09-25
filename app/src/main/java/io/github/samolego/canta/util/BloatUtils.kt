package io.github.samolego.canta.util

import android.os.Parcelable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

const val DEFAULT_BLOAT_URL =
        "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"

/**
 * App bloat information, parsed from the UAD json.
 */
@Parcelize
data class BloatData(
    internal val installData: InstallData?,
    internal val description: String?,
    internal val removal: RemovalRecommendation?,
    val dependencies: List<String> = emptyList(),
    val neededBy: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val suggestions: String? = null,
) : Parcelable {
    companion object {
        fun fromJson(json: JSONObject): BloatData {
            return BloatData(
                installData = InstallData.byNameIgnoreCaseOrNull(json.optString("list")),
                description = json.opt("description") as? String,
                removal = RemovalRecommendation.byNameIgnoreCaseOrNull(json.optString("removal")),
                dependencies = json.stringList("dependencies"),
                neededBy = json.stringList("neededBy"),
                labels = json.stringList("labels"),
                suggestions = (json.opt("suggestions") as? String)?.takeIf { it.isNotBlank() },
            )
        }

        private fun JSONObject.stringList(key: String): List<String> {
            val array = optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).mapNotNull {
                (array.opt(it) as? String)?.takeIf(String::isNotBlank)
            }
        }
    }
}

/**
 * Enum class to represent the removal recommendation, from the UAD list.
 */
enum class RemovalRecommendation(
    val icon: ImageVector,
    val badgeColor: Color,
    val description: String
) {
    RECOMMENDED(
        Icons.Default.Check,
        Color.Green,
        "Pointless or outright negative packages, and/or apps available through Google Play."
    ),
    ADVANCED(
        Icons.Default.Settings,
        Color.Yellow,
        "Breaks obscure or minor parts of functionality, or apps that aren't easily enabled/installed through Settings/Google Play. This category is also used for apps that are useful (default keyboard/gallery/launcher/music app.) but that can easily be replaced by a better alternative."
    ),
    EXPERT(
        Icons.Default.Warning,
        Color.Red,
        "Breaks widespread and/or important functionality, but nothing important to the basic operation of the operating system. Removing an 'Expert' package should not bootloop the device (unless mentioned in the description) but we can't guarantee it 100%."
    ),
    UNSAFE(
        Icons.Default.Close,
        Color.Magenta,
        "Can break vital parts of the operating system. Removing an 'Unsafe' package have an extremely high risk of bootlooping your device."
    ),
    SYSTEM(
        Icons.Default.Android,
        Color.DarkGray,
        "System apps are apps that come pre-installed with your device."
    );

    companion object {
        fun byNameIgnoreCaseOrNull(input: String): RemovalRecommendation? {
            return entries.firstOrNull { it.name.equals(input, true) }
        }
    }
}

/**
 * Represents the install data from the UAD list.
 */
enum class InstallData {
    OEM,
    CARRIER,
    GOOGLE,
    AOSP,
    MISC;

    companion object {
        fun byNameIgnoreCaseOrNull(input: String): InstallData? {
            return entries.firstOrNull { it.name.equals(input, true) }
        }
    }
}
