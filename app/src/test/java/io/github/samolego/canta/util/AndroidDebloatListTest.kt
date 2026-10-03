package io.github.samolego.canta.util

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidDebloatListTest {
    private val categories = listOf("aosp", "carrier", "google", "misc", "oem")

    internal fun archive(files: Map<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            files.forEach { (path, text) ->
                zip.putNextEntry(ZipEntry("android-debloat-list-master/$path"))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()

    internal fun fixture(extra: Map<String, String> = emptyMap()): ByteArray = archive(
        categories.associate { "$it.json" to "[]" } + mapOf("google.json" to """[{
            "id":"com.example.browser", "label":"Stock Browser", "description":"Browser description.",
            "removal":"replace", "warning":"Install a replacement first.",
            "dependencies":["android"], "required_by":["com.example.client"],
            "tags":["tracking"], "web":["https://example.com/report"], "suggestions":"browsers"
        }]""", "suggestions/browsers.json" to """[{
            "id":"org.example.browser", "label":"Open Browser", "source":"fg",
            "repo":"https://example.com/browser", "reason":"Only works on selected devices."
        }]""") + extra
    )

    @Test fun importsGuidanceRelationsAndActualReplacementApps() {
        val json = parseAndroidDebloatArchive(fixture())
        val data = BloatData.fromJson(json.getJSONObject("com.example.browser"))
        assertEquals(InstallData.GOOGLE, data.installData)
        assertEquals(RemovalRecommendation.ADVANCED, data.removal)
        assertEquals("Browser description.", data.description)
        assertEquals("Install a replacement first.", data.warning)
        assertEquals("Stock Browser", data.label)
        assertEquals(listOf("android"), data.dependencies)
        assertEquals(listOf("com.example.client"), data.neededBy)
        assertEquals(listOf("tracking"), data.labels)
        assertEquals(listOf("https://example.com/report"), data.web)
        assertEquals("Android Debloat List (MuntashirAkon)", data.source)
        assertEquals("Open Browser", data.alternatives.single().label)
        assertEquals("https://example.com/browser", data.alternatives.single().repo)
        assertEquals("Only works on selected devices.", data.alternatives.single().reason)
        assertFalse(json.getJSONObject("com.example.browser").has("verdict"))
    }

    @Test fun mapsAllFourRemovalLevelsAcrossAllFiveCategories() {
        val levels = mapOf("delete" to RemovalRecommendation.RECOMMENDED, "replace" to RemovalRecommendation.ADVANCED,
            "caution" to RemovalRecommendation.EXPERT, "unsafe" to RemovalRecommendation.UNSAFE)
        val files = categories.associate { category -> "$category.json" to levels.keys.map { level ->
            """{"id":"com.$category.$level","description":"Description","removal":"$level"}"""
        }.joinToString(prefix = "[", postfix = "]") }
        val json = parseAndroidDebloatArchive(archive(files))
        assertEquals(20, json.length())
        categories.forEach { category -> levels.forEach { (level, risk) ->
            val data = BloatData.fromJson(json.getJSONObject("com.$category.$level"))
            assertEquals(InstallData.byNameIgnoreCaseOrNull(category), data.installData)
            assertEquals(risk, data.removal)
        } }
    }

    @Test fun rejectsIncompleteArchivesUnknownRemovalAndDuplicatePackages() {
        val valid = """[{"id":"com.example.app","description":"Description","removal":"delete"}]"""
        for (files in listOf(
            mapOf("google.json" to valid),
            categories.associate { "$it.json" to "[]" } + mapOf("google.json" to valid.replace("delete", "unknown")),
            categories.associate { "$it.json" to "[]" } + mapOf("google.json" to valid, "oem.json" to valid),
        )) assertThrows(Exception::class.java) { parseAndroidDebloatArchive(archive(files)) }
    }

    @Test fun rejectsMissingReplacementListAndOversizedExpandedArchive() {
        assertThrows(Exception::class.java) { parseAndroidDebloatArchive(fixture(mapOf("suggestions/browsers.json" to "broken"))) }
        assertThrows(Exception::class.java) { parseAndroidDebloatArchive(fixture(mapOf("google.json" to """[{
            "id":"com.example.app","description":"Description","removal":"replace","suggestions":"missing"
        }]"""))) }
        assertThrows(Exception::class.java) { parseAndroidDebloatArchive(fixture(mapOf("ignored.txt" to "x".repeat(10 * 1024 * 1024)))) }
    }

    @Test fun bundleUsesOnlyTheRequestedSourceAndIncludesReplacementWarnings() {
        val json = parseAndroidDebloatArchive(java.io.File("src/main/assets/android-debloat-list.zip").readBytes())
        val data = json.keys().asSequence().map { BloatData.fromJson(json.getJSONObject(it)) }.toList()
        assertEquals(4801, data.size)
        assertTrue(data.all { it.source == "Android Debloat List (MuntashirAkon)" })
        assertTrue(data.all { it.installData != null && it.removal != null })
        assertTrue(data.any { it.neededBy.isNotEmpty() })
        val browser = BloatData.fromJson(json.getJSONObject("com.android.chrome"))
        assertTrue(browser.warning!!.contains("passkeys"))
        assertEquals(listOf("Vanadium", "Cromite"), browser.alternatives.map { it.label })
        assertEquals("GrapheneOS only", browser.alternatives.first().reason)
    }
}
