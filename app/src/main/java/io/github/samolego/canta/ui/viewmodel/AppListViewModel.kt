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
import kotlinx.coroutines.flow.first
import io.github.samolego.canta.util.RemovalRecommendation
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
            it.isUninstalled == (action in setOf(PackageAction.REINSTALL, PackageAction.REMOVE_UPDATES)) }
        if (selected.isNotEmpty()) pendingAction = PackageActionRequest(action, selectedUserId, selected)
    }

    suspend fun inspectUpdates(request: PackageActionRequest): List<UpdateImpact> =
        if (request.action in setOf(PackageAction.UNINSTALL, PackageAction.REMOVE_UPDATES))
            request.apps.filter { it.isUpdatedSystemApp }.map { packageOps.inspectUpdates(it.packageName, request.userId) }
        else emptyList()

    suspend fun inspectSafety(request: PackageActionRequest) =
        CanaServices.getInstance().safety.inspect(request.apps.map { it.packageName }, request.userId,
            removing = request.action in setOf(PackageAction.UNINSTALL, PackageAction.UNINSTALL_KEEP_DATA))

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
                    PackageAction.UNINSTALL_KEEP_DATA -> packageOps.uninstall(app.packageName, request.userId,
                        batchId = batchId, approvedWarnings = approvedWarnings[app.packageName].orEmpty(), keepData = true)
                    PackageAction.DISABLE, PackageAction.ENABLE -> packageOps.setEnabled(app.packageName, request.userId,
                        request.action == PackageAction.ENABLE, approvedWarnings[app.packageName].orEmpty(), batchId)
                    PackageAction.SUSPEND, PackageAction.UNSUSPEND -> packageOps.setSuspended(app.packageName, request.userId,
                        request.action == PackageAction.SUSPEND, approvedWarnings[app.packageName].orEmpty(), batchId)
                }.also { if (it.success && request.userId == selectedUserId) selectedApps.remove(app.packageName) }
            }
            if (request.userId == selectedUserId) loadInstalled(context.packageManager, context)
            BatchResult(results + request.apps.filter { it.packageName !in included }.map {
                OperationResult(true, context.getString(R.string.operation_skipped), skipped = true)
            }, batchId)
        } finally { isOperating = false }
    }

    suspend fun undoBatch(context: Context, batchId: String): BatchResult = withContext(Dispatchers.Main) {
        if (isOperating) return@withContext BatchResult(listOf(OperationResult(false, context.getString(R.string.operation_busy))))
        isOperating = true
        try {
            val records = CanaServices.getInstance().history.records.first().filter { it.batchId == batchId && it.changed }.asReversed()
            val undoBatch = UUID.randomUUID().toString()
            val results = records.map { packageOps.undo(it, undoBatch) }
            loadInstalled(context.packageManager, context)
            BatchResult(results)
        } finally { isOperating = false }
    }

    val defaultAction: PackageAction get() {
        val selected = apps.filter { it.packageName in selectedApps }
        return when {
            selected.isNotEmpty() && selected.all { it.isDisabled } -> PackageAction.ENABLE
            selected.any { it.removalInfo in setOf(RemovalRecommendation.EXPERT, RemovalRecommendation.UNSAFE) } -> PackageAction.DISABLE
            else -> PackageAction.UNINSTALL
        }
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
    var sortOrder by mutableStateOf(AppSort.NAME)
    var usageAvailable by mutableStateOf(false)
        private set

    val selectedAppsSorted by derivedStateOf {
        sortedList.filter { selectedApps.contains(it.packageName) }
    }

    private val nameComparator = compareBy(Collator.getInstance(Locale.getDefault()), AppInfo::name)
    private val sortedList by derivedStateOf {
        val comparator = when (sortOrder) {
            AppSort.NAME -> nameComparator
            AppSort.SIZE -> compareByDescending<AppInfo> { it.apkSizeBytes }.then(nameComparator)
            AppSort.LAST_USED -> compareByDescending<AppInfo> { it.lastUsed ?: Long.MIN_VALUE }.then(nameComparator)
        }
        apps.filter { selectedFilter.shouldShow(it) }.sortedWith(comparator)
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
        usageAvailable = false
        try {
            val packages = withContext(Dispatchers.IO) {
                val loaded = packageManager.getAllPackagesInfo(userId)
                val usage = runCatching { io.github.samolego.canta.ops.UsageRepository(context.applicationContext).lastUsed(userId) }.getOrNull()
                loaded.map { it.copy(lastUsed = if (usage == null || it.isUninstalled) null else usage[it.packageName] ?: 0L) } to (usage != null)
            }
            if (generation != loadGeneration || userId != selectedUserId) return@withContext
            apps = packages.first
            usageAvailable = packages.second
            if (!usageAvailable) {
                if (sortOrder == AppSort.LAST_USED) sortOrder = AppSort.NAME
                if (selectedFilter == Filter.unused) selectedFilter = Filter.any
            }
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

enum class AppSort(val title: Int) { NAME(R.string.sort_name), SIZE(R.string.sort_size), LAST_USED(R.string.sort_last_used) }

enum class PackageAction(val label: Int) {
    UNINSTALL(R.string.uninstall), REINSTALL(R.string.reinstall), REMOVE_UPDATES(R.string.remove_updates),
    DISABLE(R.string.disable_app), ENABLE(R.string.enable_app), SUSPEND(R.string.suspend_app),
    UNSUSPEND(R.string.unsuspend_app), UNINSTALL_KEEP_DATA(R.string.uninstall_keep_data),
}

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
