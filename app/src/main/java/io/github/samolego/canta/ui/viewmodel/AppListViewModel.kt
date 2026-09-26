package io.github.samolego.canta.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.icu.text.Collator
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import io.github.samolego.canta.R
import io.github.samolego.canta.util.optionalTextArgument
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
import kotlinx.coroutines.launch
import io.github.samolego.canta.ops.BatchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import io.github.samolego.canta.util.RemovalRecommendation
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

class AppListViewModel(
    private val bloatListLoader: (suspend (Context, Boolean) -> JSONObject)? = null,
) : ViewModel() {
    private val packageOps get() = CanaServices.getInstance().packageOps
    var isOperating by mutableStateOf(false)
        private set
    var pendingAction by mutableStateOf<PackageActionRequest?>(null)

    init { viewModelScope.launch {
        CanaServices.getInstance().batches.active.collect { isOperating = it != null }
    } }

    val leftoverUpdates by derivedStateOf { apps.filter { it.isUninstalled && it.isUpdatedSystemApp } }

    fun requestAction(action: PackageAction, packages: List<String> = selectedApps.keys.toList(), userId: Int = selectedUserId) {
        if (isOperating) return
        val selected = inventory.state(userId).apps.filter { it.packageName in packages &&
            it.isUninstalled == (action in setOf(PackageAction.REINSTALL, PackageAction.REMOVE_UPDATES)) }
        if (packages.isNotEmpty()) pendingAction = PackageActionRequest(action, userId, selected, packages.distinct())
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
    ): BatchResult {
        val appContext = context.applicationContext
        val targets = request.requestedPackages.toList()
        val eligible = request.apps.map { it.packageName }.toSet()
        val checked = included.toSet()
        val downgrades = approvedDowngrades.mapValues { it.value.toSet() }
        val warnings = approvedWarnings.mapValues { it.value.toSet() }
        return CanaServices.getInstance().batches.run(appContext.getString(request.action.label),
            targets.map { BatchItem(it, it, request.userId, request.action.name.lowercase()) }) { item, batchId ->
            val pkg = item.packageName
            if (pkg !in checked || pkg !in eligible) OperationResult(true,
                "$pkg: ${appContext.getString(R.string.operation_skipped)}", skipped = true)
            else when (request.action) {
                PackageAction.REINSTALL -> packageOps.reinstall(pkg, item.userId, batchId)
                PackageAction.UNINSTALL -> packageOps.uninstall(pkg, item.userId, resetToFactory,
                    batchId, downgrades[pkg].orEmpty(), warnings[pkg].orEmpty())
                PackageAction.REMOVE_UPDATES -> packageOps.removeUpdates(pkg, item.userId, downgrades[pkg].orEmpty(), batchId)
                PackageAction.UNINSTALL_KEEP_DATA -> packageOps.uninstall(pkg, item.userId,
                    batchId = batchId, approvedWarnings = warnings[pkg].orEmpty(), keepData = true)
                PackageAction.DISABLE, PackageAction.ENABLE -> packageOps.setEnabled(pkg, item.userId,
                    request.action == PackageAction.ENABLE, warnings[pkg].orEmpty(), batchId)
                PackageAction.SUSPEND, PackageAction.UNSUSPEND -> packageOps.setSuspended(pkg, item.userId,
                    request.action == PackageAction.SUSPEND, warnings[pkg].orEmpty(), batchId)
            }.also { if (it.success && !it.skipped && item.userId == selectedUserId) selectedApps.remove(pkg) }
        }
    }

    suspend fun undoBatch(context: Context, batchId: String): BatchResult {
        val services = CanaServices.getInstance()
        val records = services.history.records.first().filter { it.batchId == batchId && it.changed }.asReversed()
        return services.undo.undo(records)
    }

    val defaultAction: PackageAction get() {
        val selected = apps.filter { it.packageName in selectedApps }
        return when {
            selected.isNotEmpty() && selected.all { it.isDisabled } -> PackageAction.ENABLE
            selected.any { it.removalInfo in setOf(RemovalRecommendation.EXPERT, RemovalRecommendation.UNSAFE) } -> PackageAction.DISABLE
            else -> PackageAction.UNINSTALL
        }
    }

    companion object { private const val TAG = "AppListViewModel" }
    private var customInventory: io.github.samolego.canta.ops.InventoryRepository? = null
    private val inventory get() = customInventory ?: CanaServices.getInstance().inventory
    private val inventoryState get() = inventory.state(selectedUserId)
    val allApps: List<AppInfo> get() = inventoryState.apps
    private val apps: List<AppInfo> get() = allApps
    private var selectedUser by mutableStateOf<UserProfile?>(null)

    /** Users / profiles on the device, filled by [loadUsers]. */
    var users by mutableStateOf<List<UserProfile>>(emptyList())
        private set

    val selectedUserId: Int
        get() = selectedUser?.id ?: UserProfile.currentUserId

    /** The profile explicitly picked by the user, if any. */
    val selectedProfile: UserProfile?
        get() = selectedUser

    val loadError: String? get() = inventoryState.error

    var selectedApps = mutableStateSetOf<String>()

    var searchQuery by mutableStateOf("")
    var onlySystem by mutableStateOf(true)
    val isLoading: Boolean get() = inventoryState.loading
    val isLoadingBadges: Boolean get() = inventoryState.loadingMetadata

    var activeFilterIds by mutableStateOf<Set<String>>(emptySet())
    var collectionPackages by mutableStateOf<Set<String>>(emptySet())
    private var appliedViewUserId: Int? = null
    var selectedFilter: Filter
        get() = activeFilterIds.lastOrNull()?.let(Filter::fromId) ?: Filter.any
        set(value) { activeFilterIds = if (value == Filter.any) emptySet() else setOf(value.id) }
    fun toggleFilter(filter: Filter) {
        activeFilterIds = if (filter.id in activeFilterIds) activeFilterIds - filter.id else
            activeFilterIds.filterNot { Filter.fromId(it)?.group == filter.group }.toSet() + filter.id
        if (filter == Filter.user && filter.id in activeFilterIds) onlySystem = false
    }
    fun clearFilters() {
        activeFilterIds = emptySet(); collectionPackages = emptySet(); onlySystem = false; searchQuery = ""
        appliedViewUserId = null
    }
    fun applySavedView(view: io.github.samolego.canta.data.SavedView) {
        require(view.appliesTo(selectedUserId))
        appliedViewUserId = view.userId
        searchQuery = view.query
        activeFilterIds = view.filterIds.filter { id ->
            Filter.fromId(id)?.let { it != Filter.unused || usageAvailable } == true
        }.toSet()
        onlySystem = view.onlySystem
        collectionPackages = view.packages
        sortOrder = AppSort.entries.find { it.name == view.sort && (it != AppSort.LAST_USED || usageAvailable) } ?: AppSort.NAME
    }
    var sortOrder by mutableStateOf(AppSort.NAME)
    val usageAvailable: Boolean get() = inventoryState.usageAvailable

    val selectedAppsSorted by derivedStateOf {
        apps.filter { selectedApps.contains(it.packageName) }.sortedWith(nameComparator)
    }

    private val nameComparator = compareBy(Collator.getInstance(Locale.getDefault()), AppInfo::name)
    private val sortedList by derivedStateOf {
        val comparator = when (sortOrder) {
            AppSort.NAME -> nameComparator
            AppSort.SIZE -> compareByDescending<AppInfo> { it.apkSizeBytes }.then(nameComparator)
            AppSort.LAST_USED -> compareByDescending<AppInfo> { it.lastUsed ?: Long.MIN_VALUE }.then(nameComparator)
        }
        apps.filter { app -> activeFilterIds.mapNotNull(Filter::fromId).all { it.shouldShow(app) } &&
            (collectionPackages.isEmpty() || app.packageName in collectionPackages) }.sortedWith(comparator)
    }

    val appList by derivedStateOf {
        sortedList
                .filter {
                    it.name.contains(searchQuery, true) ||
                            it.packageName.contains(searchQuery, true)
                }
                .filter { it.isSystemApp || !onlySystem }
    }

    val needsReload: Boolean get() = inventoryState.stale

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
            if (appliedViewUserId != null && appliedViewUserId != user.id) {
                clearFilters()
                sortOrder = AppSort.NAME
            }
            loadInstalled(packageManager, context)
        }
    }

    suspend fun loadInstalled(
        packageManager: PackageManager,
        context: Context,
        forceRefresh: Boolean = false,
    ) {
        if (bloatListLoader != null && customInventory == null) {
            customInventory = io.github.samolego.canta.ops.InventoryRepository(context.applicationContext, viewModelScope, bloatListLoader)
        }
        val user = selectedUserId
        inventory.refresh(user, forceRefresh)
        if (user == selectedUserId && !usageAvailable) {
            if (sortOrder == AppSort.LAST_USED) sortOrder = AppSort.NAME
            activeFilterIds -= Filter.unused.id
        }
    }



}

enum class AppSort(val title: Int) { NAME(R.string.sort_name), SIZE(R.string.sort_size), LAST_USED(R.string.sort_last_used) }

enum class PackageAction(val label: Int) {
    UNINSTALL(R.string.uninstall), REINSTALL(R.string.reinstall), REMOVE_UPDATES(R.string.remove_updates),
    DISABLE(R.string.disable_app), ENABLE(R.string.enable_app), SUSPEND(R.string.suspend_app),
    UNSUSPEND(R.string.unsuspend_app), UNINSTALL_KEEP_DATA(R.string.uninstall_keep_data),
}

data class PackageActionRequest(
    val action: PackageAction,
    val userId: Int,
    val apps: List<AppInfo>,
    val requestedPackages: List<String> = apps.map { it.packageName },
)
