package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.util.apps.UserProfile
import io.github.samolego.canta.util.shizuku.ShizukuUserUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileComparisonPage(onNavigateBack: () -> Unit) {
    val services = remember { CanaServices.getInstance() }
    var profiles by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var selectedOnce by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var profileRevision by remember { mutableIntStateOf(0) }
    var profileLoading by remember { mutableStateOf(true) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var inventoryLoading by remember { mutableStateOf(false) }
    var snapshots by remember { mutableStateOf<Map<Int, InventoryState>>(emptyMap()) }
    var savedPrivacy by remember { mutableStateOf<Map<Pair<Int, String>, ComparisonSavedPrivacy>?>(null) }
    var privacyError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh) {
        profileLoading = true
        profileError = null
        try {
            profiles = withContext(Dispatchers.IO) { ShizukuUserUtils.getUsers() }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            profileError = (e.cause ?: e).message ?: e.toString()
            if (profiles.isEmpty()) profiles = listOf(UserProfile(UserProfile.currentUserId, null,
                if (UserProfile.currentUserId == 0) UserProfile.Kind.PERSONAL else UserProfile.Kind.OTHER))
        } finally {
            if (!selectedOnce) {
                selectedIds = profiles.map { it.id }
                selectedOnce = true
            } else selectedIds = selectedIds.filter { id -> profiles.any { it.id == id } }
            profileLoading = false
            profileRevision++
        }
    }

    LaunchedEffect(selectedIds, profileRevision) {
        if (profileLoading) return@LaunchedEffect
        inventoryLoading = true
        snapshots = emptyMap()
        try {
            snapshots = coroutineScope {
                selectedIds.map { userId -> async {
                    userId to try { services.inventory.snapshot(userId) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { InventoryState(error = (e.cause ?: e).message ?: e.toString()) }
                } }.awaitAll().toMap()
            }
        } finally { inventoryLoading = false }
    }

    LaunchedEffect(refresh) {
        privacyError = null
        try {
            combine(services.desiredPrivacy.blocks, services.desiredPrivacy.meteredBlocks) { network, metered ->
                val entries = mutableMapOf<Pair<Int, String>, ComparisonSavedPrivacy>()
                network.forEach { entries[it.userId to it.packageName] = ComparisonSavedPrivacy(blockNetwork = true) }
                metered.forEach { block ->
                    val key = block.userId to block.packageName
                    entries[key] = (entries[key] ?: ComparisonSavedPrivacy()).copy(denyMetered = true)
                }
                entries.toMap()
            }.collect { savedPrivacy = it }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { savedPrivacy = null; privacyError = e.message ?: e.toString() }
    }

    val selectedProfiles = profiles.filter { it.id in selectedIds }
    val inputs = selectedProfiles.map { profile ->
        val snapshot = snapshots[profile.id]
        ComparisonProfileInventory(profile.id,
            snapshot?.takeIf { it.loaded && !it.stale && it.error == null }?.apps?.map {
                ComparisonPackage(it.packageName, it.name, it.isUninstalled, it.isDisabled, it.isSuspended)
            },
            savedPrivacy?.filterKeys { it.first == profile.id }?.mapKeys { it.key.second },
        )
    }
    val rows = remember(inputs, query) { ProfileComparison.rows(inputs, query) }
    val loading = profileLoading || inventoryLoading
    val unavailable = !loading && inputs.any { it.packages == null }
    val errors = listOfNotNull(profileError, privacyError) + snapshots.values.mapNotNull { it.error }
    var showDetails by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { ScreenTopBar(onNavigateBack, { Text(stringResource(R.string.comparison_title)) }, actions = {
        IconButton(onClick = { refresh++ }, enabled = !loading) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.comparison_refresh))
        }
    }) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val controlsHeight = maxHeight * 0.55f
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState())) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.comparison_description), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.comparison_privacy_description), style = MaterialTheme.typography.bodySmall)
                        if (profileError != null) Text(stringResource(R.string.comparison_profiles_unavailable), color = MaterialTheme.colorScheme.error)
                        if (privacyError != null) Text(stringResource(R.string.comparison_privacy_unavailable), color = MaterialTheme.colorScheme.error)
                        if (unavailable) Text(stringResource(R.string.comparison_inventory_unavailable), color = MaterialTheme.colorScheme.error)
                        if (errors.isNotEmpty() || unavailable) {
                            Row {
                                TextButton(onClick = { refresh++ }, enabled = !loading) { Text(stringResource(R.string.comparison_retry)) }
                                if (errors.isNotEmpty()) TextButton(onClick = { showDetails = !showDetails }) { Text(stringResource(R.string.comparison_details)) }
                            }
                            if (showDetails) Text(errors.distinct().joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(stringResource(R.string.comparison_profiles), style = MaterialTheme.typography.titleSmall)
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        profiles.forEach { profile -> FilterChip(
                            selected = profile.id in selectedIds,
                            onClick = { selectedIds = if (profile.id in selectedIds) selectedIds - profile.id else selectedIds + profile.id },
                            label = { Text(comparisonProfileLabel(profile)) },
                        ) }
                    }
                    OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.comparison_search)) }, singleLine = true,
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.comparison_clear_search))
                        } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        Text(stringResource(R.string.comparison_loading), Modifier.padding(16.dp))
                    }
                    if (selectedProfiles.size == 1 && !profileLoading) Text(stringResource(
                        if (profiles.size > 1) R.string.comparison_one_profile else R.string.comparison_one_available),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                    if (rows.isNotEmpty()) {
                        Text(stringResource(R.string.comparison_package_count, rows.size), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall)
                        if (selectedProfiles.size > 1) Text(stringResource(R.string.comparison_scroll_hint), Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                when {
                    selectedProfiles.isEmpty() && !loading -> Text(stringResource(R.string.comparison_choose_profiles), Modifier.padding(16.dp))
                    rows.isEmpty() && !loading -> {
                        val message = when {
                            query.isNotBlank() -> R.string.comparison_no_matches
                            inputs.isNotEmpty() && inputs.all { it.packages == null } -> R.string.comparison_no_inventory
                            else -> R.string.comparison_no_apps
                        }
                        Text(stringResource(message), Modifier.padding(16.dp))
                        if (query.isNotBlank()) TextButton(onClick = { query = "" }, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(stringResource(R.string.comparison_clear_search))
                        }
                    }
                    rows.isNotEmpty() -> ProfileComparisonMatrix(rows, selectedProfiles, loading, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProfileComparisonMatrix(rows: List<ProfileComparisonRow>, profiles: List<UserProfile>, loading: Boolean, modifier: Modifier = Modifier) {
    val appWidth = 200.dp
    val cellWidth = 190.dp
    val matrixWidth = appWidth + cellWidth * profiles.size
    val labels = profiles.associate { it.id to comparisonProfileLabel(it) }
    Column(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.width(matrixWidth)) {
                Text(stringResource(R.string.comparison_app), Modifier.width(appWidth).padding(12.dp), style = MaterialTheme.typography.titleSmall)
                profiles.forEach { Text(labels.getValue(it.id), Modifier.width(cellWidth).padding(12.dp), style = MaterialTheme.typography.titleSmall) }
            }
        }
        HorizontalDivider(Modifier.width(matrixWidth))
        LazyColumn(Modifier.width(matrixWidth).weight(1f)) {
            items(rows, key = { it.packageName }) { row ->
                Row {
                    Column(Modifier.width(appWidth).padding(12.dp)) {
                        Text(row.name, style = MaterialTheme.typography.bodyMedium)
                        Text(row.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    row.cells.forEach { cell ->
                        val cellLabel = stringResource(R.string.comparison_cell_context, row.packageName, labels.getValue(cell.userId))
                        Column(Modifier.width(cellWidth).padding(12.dp).semantics(mergeDescendants = true) { contentDescription = cellLabel },
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(when {
                                loading && cell.status == ComparisonPackageStatus.UNAVAILABLE -> R.string.comparison_loading
                                cell.status == ComparisonPackageStatus.INSTALLED -> R.string.comparison_installed
                                cell.status == ComparisonPackageStatus.REMOVED -> R.string.comparison_removed
                                cell.status == ComparisonPackageStatus.ABSENT -> R.string.comparison_absent
                                else -> R.string.comparison_unavailable
                            }), style = MaterialTheme.typography.bodyMedium)
                            if (cell.disabled) Text(stringResource(R.string.comparison_disabled), style = MaterialTheme.typography.bodyMedium)
                            if (cell.suspended) Text(stringResource(R.string.comparison_suspended), style = MaterialTheme.typography.bodyMedium)
                            when (val privacy = cell.savedPrivacy) {
                                null -> Text(stringResource(R.string.comparison_privacy_unknown), style = MaterialTheme.typography.bodySmall)
                                ComparisonSavedPrivacy() -> Text(stringResource(R.string.comparison_privacy_none), style = MaterialTheme.typography.bodySmall)
                                else -> {
                                    if (privacy.blockNetwork) Text(stringResource(R.string.comparison_saved_network), style = MaterialTheme.typography.bodySmall)
                                    if (privacy.denyMetered) Text(stringResource(R.string.comparison_saved_metered), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun comparisonProfileLabel(profile: UserProfile): String = stringResource(R.string.comparison_profile,
    profile.name ?: stringResource(when (profile.kind) {
        UserProfile.Kind.PERSONAL -> R.string.profile_personal
        UserProfile.Kind.WORK -> R.string.profile_work
        UserProfile.Kind.CLONE -> R.string.profile_clone
        UserProfile.Kind.PRIVATE -> R.string.profile_private
        UserProfile.Kind.USER -> R.string.profile_user
        UserProfile.Kind.OTHER -> R.string.profile_other
    }), profile.id)
