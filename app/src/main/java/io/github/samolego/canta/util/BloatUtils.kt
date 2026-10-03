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

/**
 * App guidance from Android Debloat List or a custom package-keyed JSON list.
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
    /** Which list produced this entry. */
    val source: String? = null,
    val label: String? = null,
    val warning: String? = null,
    val web: List<String> = emptyList(),
    val alternatives: List<SuggestedApp> = emptyList(),
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
                source = (json.opt("source") as? String)?.takeIf { it.isNotBlank() },
                label = json.optionalText("label"),
                warning = json.optionalText("warning"),
                web = json.stringList("web"),
                alternatives = json.optJSONArray("alternatives")?.let { array ->
                    (0 until array.length()).mapNotNull { SuggestedApp.fromJson(array.optJSONObject(it)) }
                }.orEmpty(),
            )
        }
    }
}

/** An upstream replacement, including any restrictions on the recommendation. */
@Parcelize
data class SuggestedApp(
    val id: String,
    val label: String,
    val repo: String,
    val reason: String? = null,
    val source: String? = null,
) : Parcelable {
    companion object {
        fun fromJson(json: JSONObject?): SuggestedApp? {
            json ?: return null
            return SuggestedApp(json.optionalText("id") ?: return null,
                json.optionalText("label") ?: return null, json.optionalText("repo") ?: return null,
                json.optionalText("reason"), json.optionalText("source"))
        }
    }
}

private fun JSONObject.optionalText(key: String): String? = (opt(key) as? String)?.takeIf { it.isNotBlank() }

private fun JSONObject.stringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull {
        (array.opt(it) as? String)?.takeIf(String::isNotBlank)
    }
}

/**
 * Cana's badges for Android Debloat List's delete / replace / caution / unsafe guidance.
 */
enum class RemovalRecommendation(
    val icon: ImageVector,
    val badgeColor: Color,
    val description: String
) {
    RECOMMENDED(
        Icons.Default.Check,
        Color.Green,
        "The selected debloat list recommends removing this app. Read its description and warnings."
    ),
    ADVANCED(
        Icons.Default.Settings,
        Color.Yellow,
        "Replacement or additional review is needed. Removing this app may affect features; read its description and warnings."
    ),
    EXPERT(
        Icons.Default.Warning,
        Color.Red,
        "The selected debloat list advises caution: removing this app may be unsafe. Read its description and warnings."
    ),
    UNSAFE(
        Icons.Default.Close,
        Color.Magenta,
        "The selected debloat list says removing this app is unsafe. Read the warning about the functionality that would break."
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
 * Represents the five Android Debloat List categories.
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
