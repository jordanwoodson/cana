package io.github.samolego.canta.util

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BloatDataTest {
    @Test fun recognizesEveryUadCategoryRegardlessOfCase() {
        for (category in listOf("Google", "Oem", "Carrier", "Aosp", "Misc")) {
            assertNotNull(category, InstallData.byNameIgnoreCaseOrNull(category))
            assertNotNull(category, InstallData.byNameIgnoreCaseOrNull(category.lowercase()))
        }
        assertNull(InstallData.byNameIgnoreCaseOrNull("FutureCategory"))
    }

    @Test fun absentOrUnknownMetadataIsUnclassified() {
        val missing = BloatData.fromJson(JSONObject())
        assertNull(missing.installData)
        assertNull(missing.description)
        assertNull(missing.removal)
        val unknown = BloatData.fromJson(JSONObject("""{"list":"FutureCategory","removal":"Unknown"}"""))
        assertNull(unknown.installData)
        assertNull(unknown.removal)
    }

    @Test fun parsesRelationsLabelsAndSuggestionCategory() {
        val parsed = BloatData.fromJson(JSONObject("""{
            "list":"Google", "description":"Camera", "removal":"Advanced",
            "dependencies":["android"], "neededBy":["com.example.client"],
            "labels":["tracking"], "suggestions":"cameras"
        }"""))
        assertEquals(listOf("android"), parsed.dependencies)
        assertEquals(listOf("com.example.client"), parsed.neededBy)
        assertEquals(listOf("tracking"), parsed.labels)
        assertEquals("cameras", parsed.suggestions)
    }

    @Test fun nullAndMalformedOptionalValuesDoNotBecomeFakePackageNames() {
        val parsed = BloatData.fromJson(JSONObject("""{
            "list":null, "description":null, "removal":null,
            "dependencies":[null, 17, "", "android"], "neededBy":null,
            "labels":{}, "suggestions":null
        }"""))
        assertNull(parsed.description)
        assertNull(parsed.suggestions)
        assertEquals(listOf("android"), parsed.dependencies)
        assertTrue(parsed.neededBy.isEmpty())
        assertTrue(parsed.labels.isEmpty())
    }
}
