package io.github.samolego.canta.ops

import io.github.samolego.canta.util.*
import org.junit.Assert.*
import org.junit.Test

class PresetJsonTest {
    @Test fun profileHintIsOptionalAndSurvivesRoundtripIncludingFutureKinds() {
        for (kind in listOf("WORK", "FUTURE_PROFILE")) {
            val preset = CantaPresetData("Profile", "", 1, setOf("test.app"), profileKind = kind)
            assertEquals(kind, PresetJson.decode(PresetJson.encode(preset)).profileKind)
        }
        assertNull(PresetJson.decode("{\"name\":\"Old\",\"apps\":[]}").profileKind)
    }
    @Test fun oldStringAndObjectArraysKeepUninstalledAndOtherProfilePackages() {
        for (apps in listOf("[\"other.profile.app\"]", "[{\"packageName\":\"other.profile.app\"}]")) {
            val preset = PresetJson.decode("{\"name\":\"Old\",\"description\":\"\",\"createdDate\":1,\"apps\":$apps}")
            assertEquals(setOf("other.profile.app"), preset.apps)
            assertTrue(preset.lockdown.isEmpty())
            assertEquals("1.0", preset.version)
        }
    }
    @Test fun lockdownSurvivesJsonRoundtripWithAllSettings() {
        val preset = CantaPresetData("Privacy", "", 1, setOf("remove.me"), lockdown = listOf(
            LockdownSettings("keep.me", true, true, true, true)))
        val restored = PresetJson.decode(PresetJson.encode(preset))
        assertEquals(preset.copy(uuid = restored.uuid), restored)
    }
    @Test fun malformedOrConflictingActionsAreRefused() {
        assertTrue(runCatching { PresetJson.decode("{\"name\":\"x\",\"apps\":[\"bad name\"]}") }.isFailure)
        val preset = CantaPresetData("Conflict", "", 1, setOf("keep.me"), lockdown = listOf(LockdownSettings("keep.me", true)))
        assertTrue(runCatching { PresetJson.decode(PresetJson.encode(preset)) }.isFailure)
    }
}
