package io.github.samolego.canta.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import java.net.URI
import java.util.Locale

object BloatListUrlPolicy {
    /** Match the downloader's HTTP(S) support while rejecting ambiguous or credential-bearing URLs. */
    fun normalize(draft: String): String? {
        val value = draft.trim()
        if (value.isEmpty() || value.length > 4096 || value.any { it.isWhitespace() || it.isISOControl() }) return null
        val address = try { URI(value) } catch (_: Exception) { return null }
        if (address.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || address.host.isNullOrBlank() ||
            address.rawUserInfo != null || address.rawFragment != null || address.port != -1 && address.port !in 1..65535) return null
        return value
    }
}

data class BloatListUrlEditorState(
    val currentUrl: String = "",
    val draft: String = "",
    val loaded: Boolean = false,
    val editing: Boolean = false,
    val saving: Boolean = false,
    val invalid: Boolean = false,
    val saveFailed: Boolean = false,
)

class BloatListUrlEditor(initialUrl: String? = null, private val persist: suspend (String) -> Unit) {
    private val mutableState = MutableStateFlow(BloatListUrlEditorState(initialUrl.orEmpty(), initialUrl.orEmpty(), initialUrl != null))
    val state = mutableState.asStateFlow()
    fun observeSaved(url: String) {
        val state = mutableState.value
        mutableState.value = state.copy(currentUrl = url, draft = if (state.editing) state.draft else url, loaded = true)
    }

    fun begin() {
        val state = mutableState.value
        if (!state.loaded || state.saving) return
        mutableState.value = state.copy(draft = state.currentUrl, editing = true, invalid = false, saveFailed = false)
    }

    fun edit(draft: String) {
        val state = mutableState.value
        if (!state.editing || state.saving) return
        mutableState.value = state.copy(draft = draft, invalid = false, saveFailed = false)
    }

    fun cancel() {
        val state = mutableState.value
        if (state.saving) return
        mutableState.value = state.copy(draft = state.currentUrl, editing = false, invalid = false, saveFailed = false)
    }

    suspend fun save() {
        val state = mutableState.value
        if (!state.editing || state.saving) return
        val value = BloatListUrlPolicy.normalize(state.draft)
        if (value == null) { mutableState.value = state.copy(invalid = true, saveFailed = false); return }
        if (value == state.currentUrl) { cancel(); return }
        mutableState.value = state.copy(saving = true, invalid = false, saveFailed = false)
        try {
            persist(value)
            mutableState.value = BloatListUrlEditorState(value, value, loaded = true)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutableState.value = mutableState.value.copy(saveFailed = true) }
        finally { mutableState.value = mutableState.value.copy(saving = false) }
    }
}
