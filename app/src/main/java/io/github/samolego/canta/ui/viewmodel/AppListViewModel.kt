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
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ops.BatchResult
import io.github.samolego.canta.ops.OperationResult
import io.github.samolego.canta.ops.UpdateImpact
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
import java.util.UUID
import org.json.JSONObject

class AppListViewModel(
    private val bloatListLoader: suspend (Context, Boolean) -> JSONObject = { context, refresh ->
        BloatListRepository(context).load(refresh)
    },
) : ViewModel() {
    private val packageOps get() = CanaServices.getInstance().packageOps
    var isOperating by mutableStateOf(false)
        private set
    var pendingAction by mutableStateOf<PackageActionRequest?>(null)

    val leftoverUpdates by derivedStateOf { apps.filter { it.isUninstalled && it.isUpdatedSystemApp } }

    fun requestAction(action: PackageAction, packages: List<String> = selectedApps.keys.toList()) {
        if (isOperating) return
        val selected = apps.filter { it.packageName in packages &&
            it.isUninstalled == (action != PackageAction.UNINSTALL) }
        if (selected.isNotEmpty()) pendingAction = PackageActionRequest(action, selectedUserId, selected)
    }

    suspend fun inspectUpdates(request: PackageActionRequest): List<UpdateImpact> =
        request.apps.filter { it.isUpdatedSystemApp }.map { packageOps.inspectUpdates(it.packageName, request.userId) }

    suspend fun inspectSafety(request: PackageActionRequest) =
        CanaServices.getInstance().safety.inspect(request.apps.map { it.packageName }, request.userId)

    suspend fun processRequest(
        context: Context,
        request: PackageActionRequest,
        included: Set<String>,
        resetToFactory: Boolean,
        approvedDowngrades: Map<String, Set<Int>>,
        approvedWarnings: Map<String, Set<String>> = emptyMap(),
    ): BatchResult = withContext(Dispatchers.Main) {
        val packages = request.apps.filter { it.packageName in included }
        if (isOperating) return@withContext BatchResult(packages.map {
            OperationResult(false, context.getString(R.string.operation_busy))
        })
        isOperating = true
        val batchId = UUID.randomUUID().toString()
        try {
            val results = packages.map { app ->
                when (request.action) {
                    PackageAction.REINSTALL -> packageOps.reinstall(app.packageName, request.userId, batchId)
                    PackageAction.UNINSTALL -> packageOps.uninstall(app.packageName, request.userId, resetToFactory,
                        batchId, approvedDowngrades[app.packageName].orEmpty(), approvedWarnings[app.packageName].orEmpty())
                    PackageAction.REMOVE_UPDATES -> packageOps.removeUpdates(app.packageName, request.userId,
                        approvedDowngrades[app.packageName].orEmpty(), batchId)
                }.also { if (it.success && request.userId == selectedUserId) selectedApps.remove(app.packageName) }
            }
            if (request.userId == selectedUserId) loadInstalled(context.packageManager, context)
            BatchResult(results + request.apps.filter { it.packageName !in included }.map {
                OperationResult(true, context.getString(R.string.operation_skipped), skipped = true)
            })
        } finally { isOperating = false }
    }

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

    var needsReload by mutableStateOf(true)
        private set

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
        needsReload = true
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
                val list = bloatListLoader(context, forceRefresh)
                val parsed = mutableMapOf<String, BloatData>()
                for (key in list.keys()) {
                    list.optJSONObject(key)?.let { parsed[key] = BloatData.fromJson(it) }
                }
                parsed[packageName] = cantaBloatData(context)
                parsed
            }
            if (generation == loadGeneration && userId == selectedUserId) {
                apps = apps.map { it.copy(bloatData = bloatMap[it.packageName]) }
                needsReload = false
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


}

enum class PackageAction { UNINSTALL, REINSTALL, REMOVE_UPDATES }

data class PackageActionRequest(val action: PackageAction, val userId: Int, val apps: List<AppInfo>)

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
