package io.github.samolego.canta.ui.screen

import io.github.samolego.canta.ui.menu.profileName

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.data.proto.NetworkBlock
import io.github.samolego.canta.data.proto.PrivacyDesiredState
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.EmptyState
import io.github.samolego.canta.ui.component.StatusChip
import io.github.samolego.canta.ui.component.StatusTone
import io.github.samolego.canta.ui.dialog.*
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import io.github.samolego.canta.util.withPackageAuthentication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrivacyDashboardPage(onNavigateBack: () -> Unit) {
    val services = CanaServices.getInstance()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var unavailable by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val stored by remember(revision) { services.desiredPrivacy.state.catch { unavailable = true } }
        .collectAsState(initial = PrivacyDesiredState.getDefaultInstance())
    var connected by remember { mutableStateOf(ShizukuPermission.isCantaAuthorized()) }
    var loading by remember { mutableStateOf(true) }
    var connect by rememberSaveable { mutableStateOf(false) }
    var reviewPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewUser by rememberSaveable { mutableIntStateOf(0) }
    var forget by remember { mutableStateOf<Pair<NetworkBlock, PrivacyAction>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var diagnostics by remember { mutableStateOf(false) }
    val live = remember { mutableStateMapOf<Triple<String, Int, PrivacyAction>, Boolean?>() }
    LaunchedEffect(revision) {
        loading = true
        unavailable = false
        live.clear()
        try {
            services.privacy.initialize()
            connected = ShizukuPermission.isCantaAuthorized()
            val entries = services.desiredPrivacy.state.first().let { saved ->
                saved.networkBlocksList.map { it to PrivacyAction.NETWORK } + saved.meteredBlocksList.map { it to PrivacyAction.METERED }
            }
            if (!connected) services.privacy.markDisconnected()
            else for ((block, action) in entries) {
                val key = Triple(block.packageName, block.userId, action)
                try {
                    val actual = services.privacy.snapshot(block.packageName, block.userId, action)
                    live[key] = if (action == PrivacyAction.NETWORK) actual.getBoolean("chainEnabled") && actual.getInt("networkRule") == 2
                        else actual.getInt("meteredPolicy") and 1 != 0
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { live[key] = null }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { unavailable = true }
        finally { loading = false }
    }
    val entries = stored.networkBlocksList.map { it to PrivacyAction.NETWORK } + stored.meteredBlocksList.map { it to PrivacyAction.METERED }
    var appNames by remember { mutableStateOf<Map<Pair<Int, String>, String>>(emptyMap()) }
    LaunchedEffect(entries.map { it.first.userId }.distinct()) {
        val names = mutableMapOf<Pair<Int, String>, String>()
        entries.map { it.first.userId }.distinct().forEach { userId ->
            runCatching { services.inventory.snapshot(userId) }.getOrNull()?.apps?.forEach { app ->
                names[userId to app.packageName] = app.name
            }
        }
        appNames = names
    }
    Scaffold(snackbarHost = { SnackbarHost(io.github.samolego.canta.ui.LocalCanaSnackbar.current) },
        topBar = { ScreenTopBar(onNavigateBack, { Text(stringResource(R.string.privacy_dashboard)) },
            actions = { IconButton(enabled = !loading, onClick = { revision++ }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.system_refresh))
            } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.privacy_dashboard_description))
            if (!connected) PrivacyDisconnectedNotice({ connect = true }, { revision++ })
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it) }
            if (unavailable) Text(stringResource(R.string.privacy_store_unavailable), color = MaterialTheme.colorScheme.error)
            else if (!loading && entries.isEmpty()) EmptyState(Icons.Default.Security,
                stringResource(R.string.privacy_dashboard), stringResource(R.string.privacy_dashboard_empty))
            val grouped = entries.groupBy { it.first.userId to it.first.packageName }
                .toSortedMap(compareBy({ it.first }, { it.second }))
            for ((target, restrictions) in grouped) {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(appNames[target] ?: target.second, style = MaterialTheme.typography.titleMedium)
                        Text(profileName(target.first), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        for ((block, action) in restrictions.sortedBy { it.second.key }) {
                            HorizontalDivider()
                            val status = if (!connected && block.status != "needs_review") "disconnected" else block.status
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusChip(stringResource(action.title), Icons.Default.WifiOff)
                                StatusChip(stringResource(privacyDashboardStatus(status)), when (status) {
                                    "verified" -> Icons.Default.CheckCircle
                                    "failed" -> Icons.Default.Error
                                    else -> Icons.Default.Warning
                                }, when (status) {
                                    "failed" -> StatusTone.ERROR
                                    "verified" -> StatusTone.NEUTRAL
                                    else -> StatusTone.WARNING
                                })
                            }
                            val verified = if (connected) live[Triple(block.packageName, block.userId, action)] else null
                            Text(stringResource(when (verified) {
                                null -> R.string.privacy_live_unavailable
                                true -> if (action == PrivacyAction.NETWORK) R.string.privacy_live_blocked else R.string.privacy_live_restricted
                                false -> if (action == PrivacyAction.NETWORK) R.string.privacy_live_allowed else R.string.privacy_live_unrestricted
                            }), style = MaterialTheme.typography.bodySmall)
                            if (block.status == "needs_review") Text(stringResource(R.string.privacy_review_required), style = MaterialTheme.typography.bodySmall)
                            if (diagnostics && block.statusMessage.isNotBlank()) Text(block.statusMessage, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { forget = block to action }) {
                                Text(stringResource(R.string.privacy_forget_restriction, stringResource(action.title)))
                            }
                        }
                        TextButton(onClick = { reviewUser = target.first; reviewPackage = target.second }) {
                            Text(stringResource(R.string.privacy_review))
                        }
                    }
                }
            }
            if (entries.any { it.first.statusMessage.isNotBlank() }) TextButton(onClick = { diagnostics = !diagnostics }) {
                Text(stringResource(if (diagnostics) R.string.privacy_hide_diagnostics else R.string.privacy_diagnostics))
            }
        }
    }
    if (connect) ShizukuRequirementDialog({ connect = false; revision++ })
    reviewPackage?.let { pkg -> PrivacyDialog(pkg, reviewUser) { reviewPackage = null; revision++ } }
    forget?.let { (block, action) ->
        AlertDialog(onDismissRequest = { forget = null }, title = { Text(stringResource(R.string.privacy_forget)) },
            text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(block.packageName); Text(profileName(block.userId)); Text(stringResource(action.title))
                Text(stringResource(R.string.privacy_forget_explanation))
            } },
            confirmButton = { TextButton(onClick = {
                forget = null
                scope.launch { withPackageAuthentication(context) {
                    val result = services.batches.run(resources.getString(R.string.privacy_forget),
                        listOf(BatchItem("forget", block.packageName, block.userId, "${action.key}_forget"))) { _, _ ->
                        services.privacy.forgetDesired(block.packageName, block.userId, action)
                    }
                    message = result.results.joinToString("\n") { it.message }
                    revision++
                } }
            }) { Text(stringResource(R.string.privacy_forget)) } },
            dismissButton = { TextButton(onClick = { forget = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

private fun privacyDashboardStatus(status: String): Int = when (status) {
    "verified" -> R.string.privacy_dashboard_verified
    "disconnected", "pending" -> R.string.privacy_dashboard_waiting
    "failed" -> R.string.privacy_dashboard_failed
    else -> R.string.privacy_dashboard_review
}
