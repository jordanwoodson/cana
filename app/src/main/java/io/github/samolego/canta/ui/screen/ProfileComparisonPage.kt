package io.github.samolego.canta.ui.screen

import io.github.samolego.canta.ui.menu.displayName

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.InfoDisclosure
import io.github.samolego.canta.ui.dialog.ShizukuRequirementDialog
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import io.github.samolego.canta.util.apps.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileComparisonPage(onNavigateBack: () -> Unit, onOpenApp: (String, Int) -> Unit = { _, _ -> }) {
    val services = remember { CanaServices.getInstance() }
    var profiles by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var selectedOnce by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyDifferences by rememberSaveable { mutableStateOf(true) }
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
            if (!services.profiles.refresh()) error("Profile list unavailable")
            profiles = services.profiles.profiles.value
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            profileError = (e.cause ?: e).message ?: e.toString()
            profiles = services.profiles.profiles.value
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
    val rows = remember(inputs, query, onlyDifferences) {
        ProfileComparison.rows(inputs, query, onlyDifferences && selectedProfiles.size > 1)
    }
    val loading = profileLoading || inventoryLoading
    val unavailable = !loading && inputs.any { it.packages == null }
    val errors = listOfNotNull(profileError, privacyError) + snapshots.values.mapNotNull { it.error }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var connect by rememberSaveable { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(io.github.samolego.canta.ui.LocalCanaSnackbar.current) },
        topBar = { ScreenTopBar(onNavigateBack, { Text(stringResource(R.string.comparison_title)) }, actions = {
        IconButton(onClick = { refresh++ }, enabled = !loading) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.comparison_refresh))
        }
    }) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val controlsHeight = maxHeight * 0.55f
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState())) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.comparison_short_description), style = MaterialTheme.typography.bodyMedium)
                        InfoDisclosure(stringResource(R.string.comparison_about)) {
                            Text(stringResource(R.string.comparison_privacy_description), style = MaterialTheme.typography.bodySmall)
                        }
                        if (profileError != null) Text(stringResource(R.string.comparison_profiles_unavailable), color = MaterialTheme.colorScheme.error)
                        if (privacyError != null) Text(stringResource(R.string.comparison_privacy_unavailable), color = MaterialTheme.colorScheme.error)
                        if (unavailable) Text(stringResource(R.string.comparison_inventory_unavailable), color = MaterialTheme.colorScheme.error)
                        if (errors.isNotEmpty() || unavailable) {
                            FlowRow {
                                if (!ShizukuPermission.isCantaAuthorized()) TextButton(onClick = { connect = true }) {
                                    Text(stringResource(R.string.privacy_connect))
                                }
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
                            label = { Text(profile.displayName()) },
                        ) }
                    }
                    OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.comparison_search)) }, singleLine = true,
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.comparison_clear_search))
                        } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    Row(Modifier.fillMaxWidth().toggleable(onlyDifferences && selectedProfiles.size > 1,
                        enabled = selectedProfiles.size > 1, role = Role.Switch, onValueChange = { onlyDifferences = it })
                        .padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(stringResource(R.string.comparison_only_differences), Modifier.weight(1f))
                        Switch(checked = onlyDifferences && selectedProfiles.size > 1,
                            onCheckedChange = null, enabled = selectedProfiles.size > 1)
                    }
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
                    rows.isNotEmpty() -> ProfileComparisonMatrix(rows, selectedProfiles, loading, onOpenApp, Modifier.weight(1f))
                }
            }
        }
    }
    if (connect) ShizukuRequirementDialog { connect = false; refresh++ }
}

@Composable
private fun ProfileComparisonMatrix(rows: List<ProfileComparisonRow>, profiles: List<UserProfile>, loading: Boolean,
    onOpenApp: (String, Int) -> Unit, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)
    val density = LocalDensity.current
    val headerStyle = MaterialTheme.typography.titleSmall
    val cellStyle = MaterialTheme.typography.labelSmall
    val appLabel = stringResource(R.string.comparison_app)
    val labels = profiles.associate { it.id to it.displayName() }
    val statusLabels = mapOf(
        ComparisonPackageStatus.INSTALLED to stringResource(R.string.comparison_installed_short),
        ComparisonPackageStatus.REMOVED to stringResource(R.string.comparison_removed_short),
        ComparisonPackageStatus.ABSENT to stringResource(R.string.comparison_absent_short),
        ComparisonPackageStatus.UNAVAILABLE to stringResource(R.string.comparison_unavailable_short),
    )
    val loadingLabel = stringResource(R.string.comparison_loading)
    val disabledLabel = stringResource(R.string.comparison_disabled)
    val suspendedLabel = stringResource(R.string.comparison_suspended)
    val networkLabel = stringResource(R.string.comparison_network_short)
    val meteredLabel = stringResource(R.string.comparison_metered_short)
    fun textWidth(text: String, style: TextStyle) = with(density) {
        textMeasurer.measure(text, style, softWrap = false).size.width.toDp()
    }

    // Keep whole status labels and badges readable at the current font scale and locale.
    // The table scrolls horizontally, so wider content does not need to be clipped to fit.
    val measuredStatusLabels = statusLabels.values +
        if (loading && rows.any { row -> row.cells.any { it.status == ComparisonPackageStatus.UNAVAILABLE } }) {
            listOf(loadingLabel)
        } else emptyList()
    val statusWidth = measuredStatusLabels.maxOf { textWidth(it, cellStyle) } + 14.dp + 3.dp
    val pillWidth = listOf(disabledLabel, suspendedLabel, networkLabel, meteredLabel)
        .maxOf { textWidth(it, cellStyle) } + 8.dp
    val contentWidth = maxOf(statusWidth, pillWidth) + 16.dp
    val appWidth = maxOf(150.dp, textWidth(appLabel, headerStyle) + 16.dp)
    val cellWidths = labels.mapValues { (_, label) ->
        maxOf(108.dp, textWidth(label, headerStyle) + 16.dp, contentWidth)
    }
    val matrixWidth = profiles.fold(appWidth) { width, profile -> width + cellWidths.getValue(profile.id) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val needsHorizontalScroll = matrixWidth > maxWidth
        Column(Modifier.fillMaxSize()) {
            if (needsHorizontalScroll) Text(
                stringResource(R.string.comparison_scroll_hint),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Column(Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState())) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.width(matrixWidth)) {
                        Text(appLabel, Modifier.width(appWidth).padding(8.dp), style = headerStyle)
                        profiles.forEach { Text(labels.getValue(it.id), Modifier.width(cellWidths.getValue(it.id)).padding(8.dp), style = headerStyle) }
                    }
                }
                HorizontalDivider(Modifier.width(matrixWidth))
                LazyColumn(Modifier.width(matrixWidth).weight(1f)) {
                    items(rows, key = { it.packageName }) { row ->
                        Row {
                            Column(Modifier.width(appWidth).padding(8.dp)) {
                                Text(row.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                Text(row.packageName, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                            row.cells.forEach { cell ->
                                val openable = cell.status == ComparisonPackageStatus.INSTALLED || cell.status == ComparisonPackageStatus.REMOVED
                                Column(Modifier.width(cellWidths.getValue(cell.userId)).heightIn(min = 48.dp).clickable(enabled = openable) { onOpenApp(row.packageName, cell.userId) }
                                    .semantics(mergeDescendants = true) {}.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val statusText = if (loading && cell.status == ComparisonPackageStatus.UNAVAILABLE) {
                                        loadingLabel
                                    } else statusLabels.getValue(cell.status)
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(when (cell.status) {
                                            ComparisonPackageStatus.INSTALLED -> Icons.Default.CheckCircle
                                            ComparisonPackageStatus.REMOVED -> Icons.Default.Block
                                            ComparisonPackageStatus.ABSENT -> Icons.Default.RemoveCircleOutline
                                            ComparisonPackageStatus.UNAVAILABLE -> Icons.Default.HelpOutline
                                        }, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Text(statusText, style = cellStyle, maxLines = 1)
                                    }
                                    if (cell.disabled) ComparisonPill(disabledLabel)
                                    if (cell.suspended) ComparisonPill(suspendedLabel)
                                    cell.savedPrivacy?.let { privacy ->
                                        if (privacy.blockNetwork) ComparisonPill(networkLabel)
                                        if (privacy.denyMetered) ComparisonPill(meteredLabel)
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonPill(label: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(label, Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall,
            maxLines = 1)
    }
}
