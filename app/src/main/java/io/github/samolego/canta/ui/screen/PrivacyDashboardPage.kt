package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.data.proto.NetworkBlock
import io.github.samolego.canta.data.proto.PrivacyDesiredState
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.ui.component.IconClickButton
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
    var connect by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<Pair<String, Int>?>(null) }
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
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.privacy_dashboard)) }, navigationIcon = {
        IconClickButton(onClick = onNavigateBack, icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.privacy_dashboard_description))
            if (!connected) PrivacyDisconnectedNotice({ connect = true }, { revision++ })
            else TextButton(enabled = !loading, onClick = { revision++ }) { Text(stringResource(R.string.system_refresh)) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it) }
            if (unavailable) Text(stringResource(R.string.privacy_store_unavailable), color = MaterialTheme.colorScheme.error)
            else if (!loading && entries.isEmpty()) Text(stringResource(R.string.privacy_dashboard_empty))
            for ((block, action) in entries.sortedWith(compareBy({ it.first.userId }, { it.first.packageName }, { it.second.key }))) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(block.packageName, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.action_user, block.userId))
                        Text(stringResource(action.title))
                        Text(stringResource(R.string.privacy_saved_restriction))
                        Text(stringResource(privacyStatusLabel(if (!connected && block.status != "needs_review") "disconnected" else block.status)))
                        val verified = if (connected) live[Triple(block.packageName, block.userId, action)] else null
                        Text(stringResource(when (verified) {
                            null -> R.string.privacy_live_unavailable
                            true -> if (action == PrivacyAction.NETWORK) R.string.privacy_live_blocked else R.string.privacy_live_restricted
                            false -> if (action == PrivacyAction.NETWORK) R.string.privacy_live_allowed else R.string.privacy_live_unrestricted
                        }))
                        if (block.status == "needs_review") Text(stringResource(R.string.privacy_review_required), style = MaterialTheme.typography.bodySmall)
                        if (diagnostics && block.statusMessage.isNotBlank()) Text(block.statusMessage, style = MaterialTheme.typography.bodySmall)
                        FlowRow {
                            TextButton(onClick = { review = block.packageName to block.userId }) { Text(stringResource(R.string.privacy_review)) }
                            TextButton(onClick = { forget = block to action }) { Text(stringResource(R.string.privacy_forget)) }
                        }
                    }
                }
            }
            if (entries.any { it.first.statusMessage.isNotBlank() }) TextButton(onClick = { diagnostics = !diagnostics }) {
                Text(stringResource(if (diagnostics) R.string.privacy_hide_diagnostics else R.string.privacy_diagnostics))
            }
        }
    }
    if (connect) ShizukuRequirementDialog({ connect = false; revision++ }, ShizukuPermission.checkShizukuActive(context.packageManager))
    review?.let { (pkg, user) -> PrivacyDialog(pkg, user) { review = null; revision++ } }
    forget?.let { (block, action) ->
        AlertDialog(onDismissRequest = { forget = null }, title = { Text(stringResource(R.string.privacy_forget)) },
            text = { Column { Text(block.packageName); Text(stringResource(R.string.action_user, block.userId)); Text(stringResource(R.string.privacy_forget_explanation)) } },
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
