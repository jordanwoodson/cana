package io.github.samolego.canta.ops

import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.apps.UserProfile
import io.github.samolego.canta.util.shizuku.ShizukuUserUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shared, last-known-good directory for labels and the profile switcher. */
class ProfileDirectory(
    private val load: suspend () -> List<UserProfile> = {
        withContext(Dispatchers.IO) { ShizukuUserUtils.getUsers() }
    },
    private val onError: (Exception) -> Unit = { LogUtils.e("ProfileDirectory", "Failed to list users", it) },
) {
    private val mutex = Mutex()
    private val mutableProfiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles: StateFlow<List<UserProfile>> = mutableProfiles

    suspend fun refresh(): Boolean = mutex.withLock {
        try {
            mutableProfiles.value = load()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
            false
        }
    }

    fun find(id: Int): UserProfile? = profiles.value.firstOrNull { it.id == id }
}
