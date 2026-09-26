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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.ui.component.IconClickButton
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
    val scope = rememberCoroutineScope()
    val states = remember { mutableStateMapOf<SystemControl, JSONObject>() }
    val errors = remember { mutableStateMapOf<SystemControl, String>() }
    var records by remember { mutableStateOf<List<OperationRecord>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var host by remember { mutableStateOf("") }
    var initializedHost by remember { mutableStateOf(false) }
    var confirmPortalOff by remember { mutableStateOf(false) }
    var connect by remember { mutableStateOf(false) }
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
        message = resources.getString(R.string.privacy_results, if (result.success) 1 else 0, if (result.success) 0 else 1) + "\n" + result.message
        revision++
    }
    fun apply(control: SystemControl, values: Map<String, String?> = emptyMap(), saver: Boolean? = null) {
        busy = true
        scope.launch { try { showResult(services.system.apply(control, values, saver)) } finally { busy = false } }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.system_controls)) }, navigationIcon = {
        IconClickButton(onClick = onNavigateBack, icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.system_scope))
            Text(stringResource(R.string.system_permission_description), style = MaterialTheme.typography.bodySmall)
            FlowRow {
                TextButton(enabled = !busy, onClick = { revision++ }) { Text(stringResource(R.string.system_refresh)) }
                if (!ShizukuPermission.isCantaAuthorized()) TextButton(onClick = { connect = true }) { Text(stringResource(R.string.privacy_connect)) }
            }
            message?.let { Text(it, modifier = Modifier.testTag("system-result")) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            for (control in SystemControl.entries) {
                val state = states[control]
                val values = state?.optJSONObject("settings")
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(control.title()), style = MaterialTheme.typography.titleMedium)
                        if (state != null) Text(systemState(control, state), Modifier.testTag("system-${control.name}-state"))
                        errors[control]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (state == null && control !in errors) LinearProgressIndicator()
                        when (control) {
                            SystemControl.PRIVATE_DNS -> {
                                OutlinedTextField(host, { host = it }, label = { Text(stringResource(R.string.system_dns_hostname)) },
                                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("system-dns-host"))
                                FlowRow {
                                    TextButton(onClick = { host = "dns.quad9.net" }) { Text(stringResource(R.string.system_quad9)) }
                                    TextButton(onClick = { host = "one.one.one.one" }) { Text(stringResource(R.string.system_cloudflare)) }
                                }
                                Button(enabled = !busy && state != null, onClick = {
                                    val requested = runCatching { SystemPolicy.dns("hostname", host) }.getOrNull()
                                    if (requested == null) message = resources.getString(R.string.system_dns_invalid)
                                    else apply(control, requested)
                                }, modifier = Modifier.testTag("system-dns-apply")) { Text(stringResource(R.string.system_dns_apply)) }
                                FlowRow {
                                    for ((mode, label) in listOf("default" to R.string.system_default, "opportunistic" to R.string.system_automatic, "off" to R.string.system_off))
                                        TextButton(enabled = !busy && state != null, onClick = { apply(control, SystemPolicy.dns(mode)) }) { Text(stringResource(label)) }
                                }
                            }
                            SystemControl.CAPTIVE_PORTAL -> {
                                Text(stringResource(R.string.system_portal_description), style = MaterialTheme.typography.bodySmall)
                                values?.optString("captive_portal_https_url")?.takeIf { it.isNotBlank() && it != "null" }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                FlowRow {
                                    TextButton(enabled = !busy && state != null, onClick = { apply(control, SystemPolicy.captivePortal("default")) }) { Text(stringResource(R.string.system_default)) }
                                    TextButton(enabled = !busy && state != null, onClick = { apply(control, SystemPolicy.captivePortal("graphene")) }) { Text(stringResource(R.string.system_graphene)) }
                                    TextButton(enabled = !busy && state != null, onClick = { confirmPortalOff = true }, modifier = Modifier.testTag("system-portal-off")) { Text(stringResource(R.string.system_off)) }
                                }
                            }
                            else -> FlowRow {
                                for ((enabled, label) in listOf(true to R.string.system_on, false to R.string.system_off)) {
                                    TextButton(enabled = !busy && state != null, modifier = Modifier.testTag("system-${control.name}-$enabled"), onClick = {
                                        if (control == SystemControl.DATA_SAVER) apply(control, saver = enabled)
                                        else apply(control, mapOf(control.keys.single() to if (enabled) "1" else "0"))
                                    }) { Text(stringResource(label)) }
                                }
                            }
                        }
                        val previous = records.lastOrNull { candidate -> candidate.action == "system" && candidate.undoOf.isEmpty() &&
                            (candidate.changed || !candidate.completed) &&
                            runCatching { JSONObject(candidate.previousState).optString("control") == control.name }.getOrDefault(false) &&
                            records.none { it.undoOf == candidate.id && it.completed && it.success } }
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
    if (connect) ShizukuRequirementDialog({ connect = false; revision++ }, ShizukuPermission.checkShizukuActive(context.packageManager))
}

private fun SystemControl.title(): Int = when (this) {
    SystemControl.PRIVATE_DNS -> R.string.system_private_dns
    SystemControl.CAPTIVE_PORTAL -> R.string.system_captive_portal
    SystemControl.WIFI_SCAN -> R.string.system_wifi_scan
    SystemControl.BLE_SCAN -> R.string.system_ble_scan
    SystemControl.MOBILE_DATA -> R.string.system_mobile_data
    SystemControl.DATA_SAVER -> R.string.system_data_saver
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
