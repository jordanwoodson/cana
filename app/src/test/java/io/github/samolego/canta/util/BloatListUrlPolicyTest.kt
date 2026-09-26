package io.github.samolego.canta.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BloatListUrlPolicyTest {
    @Test fun onlyHttpAddressesWithValidAuthorityCanBeSaved() {
        assertEquals("https://example.com/list.json?variant=one", BloatListUrlPolicy.normalize(" https://example.com/list.json?variant=one "))
        assertEquals("http://localhost:8080/list", BloatListUrlPolicy.normalize("http://localhost:8080/list"))
        for (bad in listOf("", "file:///list.json", "https:///path", "https://bad host/list", "https://user:secret@example.com/list",
            "https://example.com/list#fragment", "https://example.com:99999/list", "https://example.com\n.evil/list")) {
            assertNull(bad, BloatListUrlPolicy.normalize(bad))
        }
    }

    @Test fun typingAndCancelDoNotChangeSavedUrlOrCacheMetadata() {
        var saved = "https://old.example/list"
        var writes = 0
        val editor = BloatListUrlEditor(saved) { saved = it; writes++ }
        editor.begin()
        editor.edit("https://draft.example/list")
        assertEquals("https://draft.example/list", editor.state.value.draft)
        assertTrue(editor.state.value.editing)
        assertEquals("https://old.example/list", saved)
        assertEquals(0, writes)
        editor.cancel()
        assertEquals("https://old.example/list", editor.state.value.draft)
        assertFalse(editor.state.value.editing)
        assertEquals(0, writes)
    }

    @Test fun invalidAndFailedSavesPreserveSavedUrlAndKeepDraftEditable() = runBlocking {
        val editor = BloatListUrlEditor("https://old.example/list") { error("Disk unavailable") }
        editor.begin()
        editor.edit("not a URL")
        editor.save()
        assertTrue(editor.state.value.invalid)
        assertEquals("https://old.example/list", editor.state.value.currentUrl)
        editor.edit("https://new.example/list")
        editor.save()
        assertTrue(editor.state.value.saveFailed)
        assertTrue(editor.state.value.editing)
        assertFalse(editor.state.value.saving)
        assertEquals("https://old.example/list", editor.state.value.currentUrl)
        assertEquals("https://new.example/list", editor.state.value.draft)
    }

    @Test fun onlyExplicitChangedSaveWritesAndDefaultCanBeReviewedBeforeSaving() = runBlocking {
        val writes = mutableListOf<String>()
        val editor = BloatListUrlEditor("https://old.example/list") { writes += it }
        editor.begin()
        editor.edit(" https://old.example/list ")
        editor.save()
        assertTrue(writes.isEmpty())
        editor.begin()
        editor.edit("https://default.example/list")
        assertTrue(writes.isEmpty())
        editor.save()
        assertEquals(listOf("https://default.example/list"), writes)
        assertEquals("https://default.example/list", editor.state.value.currentUrl)
        assertFalse(editor.state.value.editing)
    }

    @Test fun lateSettingsReadDoesNotOverwriteAnActiveDraft() {
        val editor = BloatListUrlEditor("https://old.example/list") { }
        editor.begin()
        editor.edit("https://draft.example/list")
        editor.observeSaved("https://changed.example/list")
        assertEquals("https://draft.example/list", editor.state.value.draft)
        editor.cancel()
        assertEquals("https://changed.example/list", editor.state.value.draft)
    }
}
