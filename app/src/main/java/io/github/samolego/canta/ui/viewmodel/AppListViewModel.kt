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
import io.github.samolego.canta.data.SettingsStore
import io.github.samolego.canta.extension.getAllPackagesInfo
import io.github.samolego.canta.extension.mutableStateSetOf
import io.github.samolego.canta.packageName
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.BloatUtils
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.apps.AppInfo
import io.github.samolego.canta.util.apps.Filter
import io.github.samolego.canta.util.apps.UserProfile
import io.github.samolego.canta.util.shizuku.ShizukuUserUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

class AppListViewModel : ViewModel() {

    companion object {
        private const val TAG = "AppListViewModel"
        private var apps by mutableStateOf<List<AppInfo>>(emptyList())

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

    suspend fun loadInstalled(packageManager: PackageManager, context: Context) {
        isLoading = true
        loadError = null
        val filesDir = context.filesDir
        val userId = selectedUserId

        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            var error: Throwable? = null
            val packages = try {
                packageManager.getAllPackagesInfo(userId)
            } catch (e: Exception) {
                error = e.cause ?: e
                LogUtils.e(TAG, "Failed to load packages of user $userId", error)
                emptyList()
            }
            // User switched profile while this was loading
            if (userId != selectedUserId) {
                return@withContext
            }
            loadError = error?.toString()
            apps = packages
            val endPackages = System.currentTimeMillis()
            LogUtils.i(TAG, "Loaded ${apps.size} packages of user $userId in ${endPackages - start}ms")
            isLoading = false

            isLoadingBadges = true
            // Load app data file
            val uadList = File(filesDir, "uad_lists.json")
            val bloatFetcher = BloatUtils()

            // Get the auto-update preference
            // ideally it should be injected using DI but for now here SettingsStore.getInstance()
            // is used as it provides the singleton instance of SettingsStore
            // earlier it was creating a new instance of SettingsStore every time loadInstalled was
            // called
            // which is a bad practice as there should only be one instance of the preferences in
            // app
            val settingsStore = SettingsStore.getInstance()
            val autoUpdate = settingsStore.autoUpdateBloatListFlow.first()

            val uadLists: JSONObject =
                    try {
                        if (!uadList.exists() ||
                                        (autoUpdate &&
                                                bloatFetcher.checkForUpdates(
                                                        settingsStore.latestCommitHashFlow.first(),
                                                        settingsStore.commitsUrlFlow.first()
                                                ))
                        ) {
                            uadList.createNewFile()
                            val (json, hash) =
                                    bloatFetcher.fetchBloatList(
                                            uadList,
                                            settingsStore.bloatListUrlFlow.first(),
                                            settingsStore.commitsUrlFlow.first()
                                    )
                            // Write the hash to settings
                            if (json.length() > 0 && hash.isNotEmpty()) {
                                // in the case of exception the fetchBloatList stills -
                                // returns the *empty json and empty hash
                                // it should only store the hash when that's not empty.
                                settingsStore.setLatestCommitHash(hash)
                            }
                            json
                        } else {
                            // Just read the file
                            val fileContent = uadList.readText()
                            if (fileContent.isBlank()) {
                                LogUtils.e(TAG, "Local uad_lists.json is blank. Retrying fetch.")
                                val (json, hash) =
                                        bloatFetcher.fetchBloatList(
                                                uadList,
                                                settingsStore.bloatListUrlFlow.first(),
                                                settingsStore.commitsUrlFlow.first()
                                        ) // Retry fetch
                                if (json.length() > 0 && hash.isNotEmpty()) {
                                    settingsStore.setLatestCommitHash(hash)
                                }
                                json
                            } else {
                                // reading the file
                                JSONObject(fileContent)
                            }
                        }
                    } catch (e: Exception) {
                        LogUtils.e(TAG, "Exception while reading uad_lists.json .", e)
                        JSONObject()
                    }

            // Parse json to map
            val bloatMap = mutableMapOf<String, BloatData>()
            for (key in uadLists.keys()) {
                val json = uadLists.getJSONObject(key)
                val bloatData = BloatData.fromJson(json)

                bloatMap[key] = bloatData
            }

            // Add Canta app info
            bloatMap[packageName] = cantaBloatData(context)

            // Assign bloat data to apps
            apps =
                    apps.map { app ->
                        if (bloatMap[app.packageName] != null) {
                            app.copy(bloatData = bloatMap[app.packageName])
                        } else {
                            app
                        }
                    }
            isLoadingBadges = false
            val end = System.currentTimeMillis()
            LogUtils.i(TAG, "Loaded badges in ${end - endPackages}ms")
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
