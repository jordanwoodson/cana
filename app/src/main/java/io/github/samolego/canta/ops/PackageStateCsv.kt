package io.github.samolego.canta.ops

import android.content.pm.ApplicationInfo
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.util.TrackerScan
import io.github.samolego.canta.util.apps.AppInfo
import io.github.samolego.canta.util.apps.UserProfile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Every package of one profile, one row each, for spreadsheets and for an AI reviewing what to debloat. */
object PackageStateCsv {
    val columns = listOf(
        "profile_id", "profile_type", "package", "label", "state", "app_type", "disabled", "suspended",
        "updated_system_app", "version_name", "version_code", "apk_size_bytes", "update_size_bytes",
        "partition", "apk_path", "installer", "target_sdk", "min_sdk", "uid", "persistent",
        "first_installed", "last_used", "unused_90_days", "debloat_list", "removal", "description", "warning",
        "dependencies", "needed_by", "labels", "alternatives", "trackers_found", "trackers",
        "last_cana_action", "last_cana_action_at", "metadata_incomplete",
    )

    fun generate(
        apps: List<AppInfo>,
        userId: Int,
        profileKind: UserProfile.Kind?,
        trackerScan: (AppInfo) -> TrackerScan? = { null },
        installer: (String) -> String? = { null },
        history: List<OperationRecord> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
    ): String {
        val lastActions = history.filter { it.userId == userId && it.completed && it.success && it.changed }
            .groupBy { it.packageName }
            .mapValues { (_, records) -> records.maxWith(compareBy({ it.sequenceNumber }, { it.timestampMs })) }
        fun time(ms: Long): String = if (ms <= 0) "" else
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(ms).truncatedTo(ChronoUnit.SECONDS).atZone(zone))
        val rows = apps.sortedBy { it.packageName }.map { app ->
            val info = app.applicationInfo
            val bloat = app.bloatData
            val scan = trackerScan(app)?.takeIf { it.error == null }
            val action = lastActions[app.packageName]
            listOf(
                userId.toString(),
                profileKind?.name?.lowercase(Locale.ROOT).orEmpty(),
                app.packageName,
                app.name,
                if (app.isUninstalled) "uninstalled" else "installed",
                if (!app.appTypeKnown) "unknown" else if (app.isSystemApp) "system" else "user",
                app.isDisabled.toString(),
                app.isSuspended.toString(),
                app.isUpdatedSystemApp.toString(),
                app.versionName,
                app.versionCode.toString(),
                app.apkSizeBytes.toString(),
                app.updateSizeBytes.toString(),
                partition(info?.sourceDir),
                info?.sourceDir.orEmpty(),
                installer(app.packageName).orEmpty(),
                info?.targetSdkVersion?.toString().orEmpty(),
                info?.minSdkVersion?.toString().orEmpty(),
                info?.uid?.toString().orEmpty(),
                info?.let { it.flags and ApplicationInfo.FLAG_PERSISTENT != 0 }?.toString().orEmpty(),
                time(app.firstInstallTime),
                when (app.lastUsed) { null -> ""; 0L -> "never"; else -> time(app.lastUsed) },
                if (app.isUninstalled || app.lastUsed == null) "" else
                    ManagementPolicy.unused90Days(app.lastUsed, app.firstInstallTime, now).toString(),
                bloat?.installData?.name?.lowercase(Locale.ROOT).orEmpty(),
                app.removalInfo?.name?.lowercase(Locale.ROOT).orEmpty(),
                app.description.orEmpty(),
                bloat?.warning.orEmpty(),
                bloat?.dependencies.orEmpty().joinToString("; "),
                bloat?.neededBy.orEmpty().joinToString("; "),
                bloat?.labels.orEmpty().joinToString("; "),
                bloat?.alternatives.orEmpty().joinToString("; ") { "${it.label} (${it.id})" },
                scan?.trackers?.size?.toString().orEmpty(),
                scan?.trackers.orEmpty().joinToString("; "),
                action?.action.orEmpty(),
                action?.let { time(it.timestampMs) }.orEmpty(),
                app.metadataIncomplete.toString(),
            )
        }
        return (listOf(columns) + rows).joinToString("") { row -> row.joinToString(",", transform = ::field) + "\n" }
    }

    /** The partition holding the APK, so preloads from system, product and vendor can be told apart. */
    internal fun partition(path: String?): String {
        val parts = path?.removePrefix("/")?.split('/')?.takeIf { it.size > 1 } ?: return ""
        return if (parts[0] == "system" && parts[1] in setOf("product", "system_ext", "vendor", "odm")) parts[1] else parts[0]
    }

    fun fileName(manufacturer: String, model: String, androidRelease: String, userId: Int, date: LocalDate): String {
        val device = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        val slug = "$device android $androidRelease".lowercase(Locale.ROOT).replace(Regex("[^a-z0-9.]+"), "-").trim('-')
        return "cana-packages-$slug-user$userId-$date.csv"
    }

    internal fun field(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || value.trim() != value)
            "\"" + value.replace("\"", "\"\"") + "\"" else value
}
