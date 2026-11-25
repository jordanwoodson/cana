package io.github.samolego.canta.ops

import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.TrackerScan
import io.github.samolego.canta.util.apps.AppInfo
import io.github.samolego.canta.util.apps.UserProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PackageStateCsvTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    private fun app(pkg: String, system: Boolean = true, uninstalled: Boolean = false, disabled: Boolean = false,
                    lastUsed: Long? = null, bloat: BloatData? = null) =
        AppInfo(pkg.substringAfterLast('.'), pkg, "1.0", 7, system, uninstalled, disabled, bloat,
            firstInstallTime = 10 * day, lastUsed = lastUsed)

    private fun record(pkg: String, action: String, sequence: Long, userId: Int = 0, changed: Boolean = true) =
        OperationRecord.newBuilder().setPackageName(pkg).setAction(action).setUserId(userId).setSequenceNumber(sequence)
            .setTimestampMs(sequence * day).setCompleted(true).setSuccess(true).setChanged(changed).build()

    /** Minimal RFC 4180 reader, so the test checks what a spreadsheet or CSV library would see. */
    private fun parse(csv: String): List<Map<String, String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>(); val field = StringBuilder(); var quoted = false; var i = 0
        while (i < csv.length) {
            val c = csv[i]
            when {
                quoted && c == '"' && csv.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && c == '\n' -> { row += field.toString(); field.clear(); rows += row; row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        val header = rows.first()
        rows.drop(1).forEach { assertEquals(header.size, it.size) }
        return rows.drop(1).map { header.zip(it).toMap() }
    }

    @Test fun everyPackageIsExportedWithItsStateAndDebloatGuidance() {
        val bloat = BloatData.fromJson(JSONObject().put("list", "Carrier").put("removal", "Recommended")
            .put("description", "Carrier \"setup\" app,\nruns on boot").put("neededBy", JSONArray().put("com.a").put("com.b"))
            .put("alternatives", JSONArray().put(JSONObject().put("id", "org.fdroid").put("label", "F-Droid").put("repo", "x"))))
        val apps = listOf(
            app("com.tmobile.setup", uninstalled = true, bloat = bloat),
            app("com.example.notes", system = false, lastUsed = now - day),
            app("com.android.chrome", disabled = true, lastUsed = 0),
        )
        val rows = parse(PackageStateCsv.generate(apps, 10, UserProfile.Kind.WORK,
            trackerScan = { if (it.packageName == "com.example.notes") TrackerScan(it.packageName, 7, listOf("Firebase Analytics", "AdMob"), 1, 1, 0) else null },
            installer = { if (it == "com.example.notes") "com.android.vending" else null },
            zone = ZoneOffset.UTC, now = now))

        assertEquals(listOf("com.android.chrome", "com.example.notes", "com.tmobile.setup"), rows.map { it["package"] })
        val (chrome, notes, setup) = rows
        assertEquals("10", setup["profile_id"]); assertEquals("work", setup["profile_type"])
        assertEquals("uninstalled", setup["state"]); assertEquals("system", setup["app_type"])
        assertEquals("carrier", setup["debloat_list"]); assertEquals("recommended", setup["removal"])
        assertEquals("Carrier \"setup\" app,\nruns on boot", setup["description"])
        assertEquals("com.a; com.b", setup["needed_by"]); assertEquals("F-Droid (org.fdroid)", setup["alternatives"])
        assertEquals("", setup["last_used"]); assertEquals("", setup["trackers_found"])

        assertEquals("installed", notes["state"]); assertEquals("user", notes["app_type"])
        assertEquals("com.android.vending", notes["installer"]); assertEquals("false", notes["unused_90_days"])
        assertEquals("2", notes["trackers_found"]); assertEquals("Firebase Analytics; AdMob", notes["trackers"])
        assertEquals("1970-01-11T00:00:00Z", notes["first_installed"])

        assertEquals("true", chrome["disabled"]); assertEquals("no recorded use", chrome["last_used"])
        assertEquals("true", chrome["unused_90_days"])
    }

    @Test fun lastCanaActionIsTheLatestChangeInTheExportedProfile() {
        val history = listOf(record("com.a", "uninstall", 1), record("com.a", "reinstall", 3),
            record("com.a", "disable", 4, changed = false), record("com.a", "uninstall", 5, userId = 10))
        val row = parse(PackageStateCsv.generate(listOf(app("com.a")), 0, UserProfile.Kind.PERSONAL,
            history = history, zone = ZoneOffset.UTC)).single()
        assertEquals("reinstall", row["last_cana_action"])
        assertEquals("1970-01-04T00:00:00Z", row["last_cana_action_at"])
    }

    @Test fun partitionIsReadFromTheApkPath() {
        assertEquals("product", PackageStateCsv.partition("/product/priv-app/Setup/Setup.apk"))
        assertEquals("product", PackageStateCsv.partition("/system/product/app/Setup/Setup.apk"))
        assertEquals("system", PackageStateCsv.partition("/system/app/Chrome/Chrome.apk"))
        assertEquals("data", PackageStateCsv.partition("/data/app/~~abc==/com.a-xyz==/base.apk"))
        assertEquals("", PackageStateCsv.partition(null))
    }

    @Test fun fileNameNamesTheDeviceProfileAndDate() {
        assertEquals("cana-packages-motorola-edge-2025-android-15-user0-2026-10-06.csv",
            PackageStateCsv.fileName("motorola", "motorola edge 2025", "15", 0, LocalDate.of(2026, 10, 6)))
        assertEquals("cana-packages-google-pixel-9-android-16.1-user10-2026-10-06.csv",
            PackageStateCsv.fileName("Google", "Pixel 9", "16.1", 10, LocalDate.of(2026, 10, 6)))
    }
}
