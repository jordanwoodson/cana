package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.ui.component.ChoiceOption
import io.github.samolego.canta.ui.component.ChoiceRow
import io.github.samolego.canta.ui.component.InfoDisclosure
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.dialog.ShizukuRequirementDialog
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SystemPage(onNavigateBack: () -> Unit) {
    val services = CanaServices.getInstance()
    val context = LocalContext.current
    val resources = LocalResources.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val states = remember { mutableStateMapOf<SystemControl, JSONObject>() }
    val errors = remember { mutableStateMapOf<SystemControl, String>() }
    var records by remember { mutableStateOf<List<OperationRecord>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var host by remember { mutableStateOf("") }
    var editCustom by remember { mutableStateOf(false) }
    var initializedHost by remember { mutableStateOf(false) }
    var confirmPortalOff by remember { mutableStateOf(false) }
    var connect by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(revision) {
        states.clear(); errors.clear()
        records = services.history.records.first()
        for (control in SystemControl.entries) {
            try {
                val value = services.system.snapshot(control)
                states[control] = value
                if (control == SystemControl.PRIVATE_DNS && !initializedHost) {
                    host = value.getJSONObject("settings").optString("private_dns_specifier", "")
                    if (host == "null") host = ""
                    initializedHost = true
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { errors[control] = e.message ?: e.toString() }
        }
    }
    fun showResult(result: OperationResult) {
        if (result.success) {
            message = null
            scope.launch { snackbar.showSnackbar(result.message) }
        } else message = result.message
        revision++
    }
    fun apply(control: SystemControl, values: Map<String, String?> = emptyMap(), saver: Boolean? = null) {
        busy = true
        scope.launch { try { showResult(services.system.apply(control, values, saver)) } finally { busy = false } }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ScreenTopBar(onNavigateBack, { Text(stringResource(R.string.device_settings)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.system_scope))
            InfoDisclosure(stringResource(R.string.system_more_info)) {
                Text(stringResource(R.string.system_permission_description), style = MaterialTheme.typography.bodySmall)
            }
            FlowRow {
                TextButton(enabled = !busy, onClick = { revision++ }) { Text(stringResource(R.string.system_refresh)) }
                if (!ShizukuPermission.isCantaAuthorized()) TextButton(onClick = { connect = true }) { Text(stringResource(R.string.privacy_connect)) }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("system-result")) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            for (control in SystemControl.entries) {
                val state = states[control]
                val values = state?.optJSONObject("settings")
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(control.title()), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(control.description()), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state != null) Text(systemState(control, state), Modifier.testTag("system-${control.name}-state"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        errors[control]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (state == null && control !in errors) LinearProgressIndicator()
                        when (control) {
                            SystemControl.PRIVATE_DNS -> {
                                val mode = values?.optString("private_dns_mode") ?: ""
                                val selected = if (editCustom || mode == "hostname") "hostname" else mode.takeIf { it in setOf("off", "opportunistic") } ?: "default"
                                ChoiceRow(listOf(
                                    ChoiceOption("default", stringResource(R.string.system_choice_default), "system-dns-default"),
                                    ChoiceOption("opportunistic", stringResource(R.string.system_automatic), "system-dns-automatic"),
                                    ChoiceOption("off", stringResource(R.string.system_off), "system-dns-off"),
                                    ChoiceOption("hostname", stringResource(R.string.system_custom), "system-dns-custom"),
                                ), selected, { choice ->
                                    if (choice == "hostname") editCustom = true
                                    else { editCustom = false; apply(control, SystemPolicy.dns(choice)) }
                                }, enabled = !busy && state != null)
                                if (selected == "hostname") {
                                    OutlinedTextField(host, { host = it }, label = { Text(stringResource(R.string.system_dns_hostname)) },
                                        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("system-dns-host"))
                                    FlowRow {
                                        TextButton(onClick = { host = "dns.quad9.net" }) { Text(stringResource(R.string.system_quad9)) }
                                        TextButton(onClick = { host = "one.one.one.one" }) { Text(stringResource(R.string.system_cloudflare)) }
                                    }
                                    Button(enabled = !busy && state != null, onClick = {
                                        keyboard?.hide()
                                        val requested = runCatching { SystemPolicy.dns("hostname", host) }.getOrNull()
                                        if (requested == null) message = resources.getString(R.string.system_dns_invalid)
                                        else apply(control, requested)
                                    }, modifier = Modifier.testTag("system-dns-apply")) { Text(stringResource(R.string.system_dns_apply)) }
                                }
                            }
                            SystemControl.CAPTIVE_PORTAL -> {
                                val selected = when {
                                    values?.optString("captive_portal_mode") == "0" -> "off"
                                    values?.optString("captive_portal_https_url") == "https://connectivitycheck.grapheneos.network/generate_204" -> "graphene"
                                    else -> "default"
                                }
                                ChoiceRow(listOf(
                                    ChoiceOption("default", stringResource(R.string.system_choice_default), "system-portal-default"),
                                    ChoiceOption("graphene", stringResource(R.string.system_graphene), "system-portal-graphene"),
                                    ChoiceOption("off", stringResource(R.string.system_off), "system-portal-off"),
                                ), selected, { choice ->
                                    if (choice == "off") confirmPortalOff = true
                                    else apply(control, SystemPolicy.captivePortal(choice))
                                }, enabled = !busy && state != null)
                            }
                            else -> {
                                val selected = if (control == SystemControl.DATA_SAVER) {
                                    if (state?.optBoolean("dataSaver") == true) "1" else "0"
                                } else values?.optString(control.keys.single())?.takeIf { it == "1" || it == "0" } ?: "default"
                                val options = buildList {
                                    if (control != SystemControl.DATA_SAVER) add(ChoiceOption("default", resources.getString(R.string.system_choice_default), "system-${control.name}-default"))
                                    add(ChoiceOption("1", resources.getString(R.string.system_on), "system-${control.name}-true"))
                                    add(ChoiceOption("0", resources.getString(R.string.system_off), "system-${control.name}-false"))
                                }
                                ChoiceRow(options, selected, { choice ->
                                    if (control == SystemControl.DATA_SAVER) apply(control, saver = choice == "1")
                                    else apply(control, mapOf(control.keys.single() to choice.takeUnless { it == "default" }))
                                }, enabled = !busy && state != null)
                            }
                        }
                        // A successful undo supersedes older changes to this control. Do not offer
                        // to revert an older record after the latest change has been undone.
                        val previous = records.lastOrNull { candidate -> candidate.action == "system" &&
                            runCatching { JSONObject(candidate.previousState).optString("control") == control.name }.getOrDefault(false) }
                            ?.takeIf { it.undoOf.isEmpty() && (it.changed || !it.completed) &&
                                records.none { undo -> undo.undoOf == it.id && undo.completed && undo.success } }
                        if (previous != null) TextButton(enabled = !busy && state != null, modifier = Modifier.testTag("system-${control.name}-revert"), onClick = {
                            busy = true
                            scope.launch { try { showResult(services.system.undo(previous)) } finally { busy = false } }
                        }) { Text(stringResource(R.string.privacy_revert)) }
                    }
                }
            }
        }
    }
    if (confirmPortalOff) AlertDialog(onDismissRequest = { confirmPortalOff = false }, title = { Text(stringResource(R.string.system_portal_off_title)) },
        text = { Text(stringResource(R.string.system_portal_off_warning)) }, confirmButton = {
            TextButton(onClick = { confirmPortalOff = false; apply(SystemControl.CAPTIVE_PORTAL, SystemPolicy.captivePortal("off")) }) { Text(stringResource(R.string.system_turn_off)) }
        }, dismissButton = { TextButton(onClick = { confirmPortalOff = false }) { Text(stringResource(R.string.cancel)) } })
    if (connect) ShizukuRequirementDialog({ connect = false; revision++ })
}

private fun SystemControl.title(): Int = when (this) {
    SystemControl.PRIVATE_DNS -> R.string.system_private_dns
    SystemControl.CAPTIVE_PORTAL -> R.string.system_captive_portal
    SystemControl.WIFI_SCAN -> R.string.system_wifi_scan
    SystemControl.BLE_SCAN -> R.string.system_ble_scan
    SystemControl.MOBILE_DATA -> R.string.system_mobile_data
    SystemControl.DATA_SAVER -> R.string.system_data_saver
}

private fun SystemControl.description(): Int = when (this) {
    SystemControl.PRIVATE_DNS -> R.string.system_dns_description
    SystemControl.CAPTIVE_PORTAL -> R.string.system_portal_summary
    SystemControl.WIFI_SCAN -> R.string.system_wifi_description
    SystemControl.BLE_SCAN -> R.string.system_ble_description
    SystemControl.MOBILE_DATA -> R.string.system_mobile_description
    SystemControl.DATA_SAVER -> R.string.system_saver_description
}

@Composable
private fun systemState(control: SystemControl, state: JSONObject): String {
    val settings = state.optJSONObject("settings")
    return when (control) {
        SystemControl.DATA_SAVER -> stringResource(if (state.getBoolean("dataSaver")) R.string.system_on else R.string.system_off)
        SystemControl.PRIVATE_DNS -> when (settings!!.optString("private_dns_mode")) {
            "hostname" -> stringResource(R.string.system_dns_configured, settings.optString("private_dns_specifier"))
            "off" -> stringResource(R.string.system_off)
            "opportunistic" -> stringResource(R.string.system_automatic)
            "", "null" -> stringResource(R.string.system_default)
            else -> stringResource(R.string.system_current_value, settings.optString("private_dns_mode"))
        }
        SystemControl.CAPTIVE_PORTAL -> when {
            settings!!.optString("captive_portal_mode") == "0" -> stringResource(R.string.system_off)
            control.keys.all { settings.isNull(it) } -> stringResource(R.string.system_default)
            settings.optString("captive_portal_https_url") == "https://connectivitycheck.grapheneos.network/generate_204" -> stringResource(R.string.system_graphene)
            else -> stringResource(R.string.system_configured)
        }
        else -> when (val value = settings!!.optString(control.keys.single())) {
            "1" -> stringResource(R.string.system_on)
            "0" -> stringResource(R.string.system_off)
            "", "null" -> stringResource(R.string.system_default)
            else -> stringResource(R.string.system_current_value, value)
        }
    }
}
