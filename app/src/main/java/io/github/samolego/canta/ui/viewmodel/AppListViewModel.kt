package io.github.samolego.canta.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.icu.text.Collator
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.github.samolego.canta.R
import io.github.samolego.canta.extension.getAllPackagesInfo
import io.github.samolego.canta.extension.mutableStateSetOf
import io.github.samolego.canta.packageName
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.BloatListRepository
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.apps.AppInfo
import io.github.samolego.canta.util.apps.Filter
import io.github.samolego.canta.util.apps.UserProfile
import io.github.samolego.canta.util.shizuku.ShizukuUserUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class AppListViewModel : ViewModel() {

    companion object {
        private const val TAG = "AppListViewModel"
        private var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private var loadGeneration = 0L

        /**
         * Profile whose apps are shown and modified, null = the one Canta runs in.
         * Kept next to [apps] so the two can never get out of sync.
         */
        private var selectedUser by mutableStateOf<UserProfile?>(null)
    }

    /** Users / profiles on the device, filled by [loadUsers]. */
    var users by mutableStateOf<List<UserProfile>>(emptyList())
        private set

    val selectedUserId: Int
        get() = selectedUser?.id ?: UserProfile.currentUserId

    /** The profile explicitly picked by the user, if any. */
    val selectedProfile: UserProfile?
        get() = selectedUser

    var loadError by mutableStateOf<String?>(null)
        private set

    var selectedApps = mutableStateSetOf<String>()

    var searchQuery by mutableStateOf("")
    var onlySystem by mutableStateOf(true)
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingBadges by mutableStateOf(false)
        private set

    var selectedFilter by mutableStateOf(Filter.any)

    val selectedAppsSorted by derivedStateOf {
        sortedList.filter { selectedApps.contains(it.packageName) }
    }

    private val nameComparator = compareBy(Collator.getInstance(Locale.getDefault()), AppInfo::name)
    private val sortedList by derivedStateOf {
        apps.filter { selectedFilter.shouldShow(it) }.sortedWith(nameComparator)
    }

    val appList by derivedStateOf {
        sortedList
                .filter {
                    it.name.contains(searchQuery, true) ||
                            it.packageName.contains(searchQuery, true)
                }
                .filter { it.isSystemApp || !onlySystem }
    }

    /** Lists the users / profiles on the device through Shizuku. */
    suspend fun loadUsers(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                users = ShizukuUserUtils.getUsers()
                LogUtils.i(TAG, "Found users: ${users.joinToString { "${it.id} (${it.kind})" }}")
                true
            } catch (e: Exception) {
                LogUtils.e(TAG, "Failed to list users", e.cause ?: e)
                false
            }
        }
    }

    /** Switches to managing apps of [user]. */
    suspend fun selectUser(user: UserProfile, packageManager: PackageManager, context: Context) {
        val changed = user.id != selectedUserId
        selectedUser = user
        if (changed) {
            LogUtils.i(TAG, "Switching to user ${user.id} (${user.kind})")
            selectedApps.clear()
            loadInstalled(packageManager, context)
        }
    }

    suspend fun loadInstalled(
        packageManager: PackageManager,
        context: Context,
        forceRefresh: Boolean = false,
    ) = withContext(Dispatchers.Main) {
        val generation = ++loadGeneration
        val userId = selectedUserId
        isLoading = true
        loadError = null
        apps = emptyList()
        try {
            val packages = withContext(Dispatchers.IO) {
                packageManager.getAllPackagesInfo(userId)
            }
            if (generation != loadGeneration || userId != selectedUserId) return@withContext
            apps = packages
            isLoading = false
            isLoadingBadges = true
            val bloatMap = withContext(Dispatchers.IO) {
                val list = BloatListRepository(context).load(forceRefresh)
                val parsed = mutableMapOf<String, BloatData>()
                for (key in list.keys()) {
                    list.optJSONObject(key)?.let { parsed[key] = BloatData.fromJson(it) }
                }
                parsed[packageName] = cantaBloatData(context)
                parsed
            }
            if (generation == loadGeneration && userId == selectedUserId) {
                apps = apps.map { it.copy(bloatData = bloatMap[it.packageName]) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to load packages or list for user $userId", e.cause ?: e)
            if (generation == loadGeneration && userId == selectedUserId) {
                loadError = (e.cause ?: e).toString()
            }
        } finally {
            if (generation == loadGeneration) {
                isLoading = false
                isLoadingBadges = false
            }
        }
    }

    /** Changes app status from installed to uninstalled or vice versa. */
    fun changeAppStatus(packageName: String) {
        apps =
                apps.map {
                    if (it.packageName == packageName) {
                        it.copy(isUninstalled = !it.isUninstalled)
                    } else {
                        it
                    }
                }
    }
}

private fun cantaBloatData(context: Context): BloatData {
    return BloatData(
            installData = null,
            description =
                    context.getString(
                            R.string.canta_description,
                            "Universal Debloater Alliance (https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation)"
                    ),
            removal = null,
    )
}
