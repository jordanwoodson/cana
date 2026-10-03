package io.github.samolego.canta.ui.screen

import io.github.samolego.canta.ui.menu.profileName

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.format.Formatter.formatShortFileSize
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.ops.ActionChoices
import io.github.samolego.canta.ui.component.*
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.openWebLink
import io.github.samolego.canta.util.apps.UserProfile
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDetailPage(target: AppTarget, onNavigateBack: () -> Unit, onAction: (PackageAction) -> Unit) {
    val services = CanaServices.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsUnavailable = stringResource(R.string.app_settings_unavailable)
    val clipboard = LocalClipboard.current
    var tab by rememberSaveable(target.packageName, target.userId) { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var showRiskInfo by remember { mutableStateOf(false) }
    var showSizeInfo by remember { mutableStateOf(false) }
    var moreActions by remember { mutableStateOf(false) }
    val state = services.inventory.state(target.userId)
    val app = state.apps.find { it.packageName == target.packageName }
    LaunchedEffect(target) { if (!state.loaded || state.stale) services.inventory.snapshot(target.userId) }
    Scaffold(snackbarHost = { SnackbarHost(io.github.samolego.canta.ui.LocalCanaSnackbar.current) }, topBar = { ScreenTopBar(
        onNavigateBack = onNavigateBack,
        title = { Text(app?.name ?: target.packageName, style = MaterialTheme.typography.titleMedium) },
        subtitle = profileName(target.userId),
    ) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                listOf(R.string.app_overview, R.string.privacy, R.string.components).forEachIndexed { index, label ->
                    Tab(tab == index, onClick = { tab = index }, text = { Text(stringResource(label), maxLines = 1) })
                }
            }
            when {
                tab == 1 -> Box(Modifier.padding(16.dp)) { io.github.samolego.canta.ui.dialog.PrivacyDialog(target.packageName, target.userId, embedded = true, onDismiss = { tab = 0 }) }
                state.loading && app == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                app == null -> Column(Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.app_target_unavailable))
                    TextButton(onClick = { scope.launch { services.inventory.snapshot(target.userId) } }) { Text(stringResource(R.string.inventory_retry)) }
                }
                tab == 0 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIconImage(app, target.userId, size = 56.dp)
                        Column(Modifier.weight(1f)) {
                            Text(app.name, style = MaterialTheme.typography.titleLarge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SelectionContainer(modifier = Modifier.weight(1f)) { Text(target.packageName,
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                                IconButton(onClick = { scope.launch { clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("Package", target.packageName))) } }) {
                                    Icon(Icons.Default.ContentCopy, stringResource(R.string.copy_package_name))
                                }
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        app.shownRisk?.let { RiskChip(it, Modifier.heightIn(min = 48.dp).clickable { showRiskInfo = true }) }
                        val stateLabel = when {
                            app.isUninstalled -> R.string.tab_removed
                            app.isDisabled -> R.string.app_state_disabled
                            app.isSuspended -> R.string.app_state_suspended
                            else -> R.string.tab_installed
                        }
                        val stateIcon = when {
                            app.isUninstalled || app.isDisabled -> Icons.Default.Block
                            app.isSuspended -> Icons.Default.PauseCircle
                            else -> Icons.Default.CheckCircle
                        }
                        StatusChip(stringResource(stateLabel), stateIcon)
                        StatusChip(stringResource(if (app.isSystemApp) R.string.scope_system_apps else R.string.scope_user_apps), Icons.Default.Android)
                        Text(stringResource(R.string.app_version_short, app.versionName, app.versionCode),
                            style = MaterialTheme.typography.labelMedium)
                        Text(if (app.apkSizeBytes > 0) formatShortFileSize(context, app.apkSizeBytes)
                            else stringResource(R.string.app_size_unavailable), style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { showSizeInfo = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Info, stringResource(R.string.app_apk_size_details), modifier = Modifier.size(18.dp))
                        }
                    }
                    val choices = ActionChoices.forSelection(listOf(app), app.isUninstalled)
                    val uninstallChoice = if (!app.isUninstalled && choices.primary != PackageAction.UNINSTALL) PackageAction.UNINSTALL else null
                    val remaining = (choices.reversible + choices.removing).filterNot { it == uninstallChoice }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAction(choices.primary) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(choices.primary.label), maxLines = 1)
                        }
                        if (uninstallChoice != null) OutlinedButton(onClick = { onAction(uninstallChoice) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text(stringResource(R.string.uninstall))
                        }
                        if (remaining.isNotEmpty()) Box {
                            IconButton(onClick = { moreActions = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options_action)) }
                            DropdownMenu(expanded = moreActions, onDismissRequest = { moreActions = false }) {
                                remaining.forEach { choice -> DropdownMenuItem(text = { Text(stringResource(choice.label)) }, onClick = {
                                    moreActions = false; onAction(choice)
                                }) }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.about_this_app), style = MaterialTheme.typography.titleMedium)
                    app.bloatData?.warning?.let { warning ->
                        CanaBanner(Icons.Default.Warning, stringResource(R.string.app_list_warning), StatusTone.WARNING,
                            details = { Text(warning, style = MaterialTheme.typography.bodyMedium) })
                    }
                    val description = app.description
                    if (description != null) {
                        val color = MaterialTheme.colorScheme.onSurface.toArgb()
                        AndroidView(factory = { TextView(it).apply { setTextIsSelectable(true); movementMethod = LinkMovementMethod.getInstance(); textSize = 16f } },
                            // List descriptions are plain text with \n between lines, which HTML would collapse
                            update = { it.text = Html.fromHtml(description.replace("\n", "<br>"), Html.FROM_HTML_MODE_COMPACT); it.setTextColor(color) },
                            modifier = Modifier.fillMaxWidth())
                    } else Text(stringResource(R.string.no_description_available), style = MaterialTheme.typography.bodyLarge)
                    app.bloatData?.source?.let { Text(stringResource(R.string.app_list_source, it), style = MaterialTheme.typography.labelMedium) }
                    val alternatives = app.bloatData?.alternatives.orEmpty()
                    if (alternatives.isNotEmpty()) {
                        Text(stringResource(R.string.app_replacements), style = MaterialTheme.typography.titleSmall)
                        alternatives.forEach { suggested ->
                            val canOpen = Uri.parse(suggested.repo).scheme in setOf("https", "http")
                            ListItem(
                                headlineContent = { Text(suggested.label) },
                                supportingContent = { Column {
                                    Text(suggested.id, style = MaterialTheme.typography.bodySmall)
                                    suggested.reason?.let { Text(it) }
                                } },
                                trailingContent = { if (canOpen) Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                modifier = Modifier.clickable(enabled = canOpen) { context.openWebLink(suggested.repo) },
                            )
                        }
                    } else app.bloatData?.suggestions?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.app_suggestions, it)) }
                    app.bloatData?.web?.filter { Uri.parse(it).scheme in setOf("https", "http") }?.takeIf { it.isNotEmpty() }?.let { links ->
                        Text(stringResource(R.string.app_references), style = MaterialTheme.typography.titleSmall)
                        links.forEach { url -> TextButton(onClick = { context.openWebLink(url) }) { Text(url) } }
                    }
                    app.bloatData?.neededBy?.takeIf { it.isNotEmpty() }?.let {
                        Text(stringResource(R.string.app_needed_by, it.joinToString()), style = MaterialTheme.typography.bodySmall)
                    }
                    app.lastUsed?.let { lastUsed -> Text(if (lastUsed == 0L) stringResource(R.string.last_used_no_record)
                        else stringResource(R.string.last_used_date, java.text.DateFormat.getDateInstance().format(java.util.Date(lastUsed)))) }
                    HorizontalDivider()
                    TrackerSummary(app)
                    HorizontalDivider()
                    if (!app.isUninstalled) ListItem(headlineContent = { Text(stringResource(R.string.open_android_settings)) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                        modifier = Modifier.clickable { scope.launch {
                            message = null
                            try {
                                if (target.userId == UserProfile.currentUserId) context.startActivity(Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:${target.packageName}")))
                                else {
                                    val result = services.shell.exec(target.settingsCommand())
                                    if (!result.success || result.message.contains("Error:", true)) {
                                        LogUtils.w("AppDetails", "Cannot open profile settings: ${result.message}")
                                        message = settingsUnavailable
                                    }
                                }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { LogUtils.e("AppDetails", "Cannot open profile settings", e); message = settingsUnavailable }
                        } })
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                app.isUninstalled -> Text(stringResource(R.string.app_removed_sections), Modifier.padding(24.dp))
                !ShizukuPermission.isCantaAuthorized() -> Text(stringResource(R.string.inventory_disconnected), Modifier.padding(24.dp))
                else -> Box(Modifier.padding(16.dp)) { io.github.samolego.canta.ui.dialog.ComponentsDialog(target.packageName, target.userId, embedded = true, onDismiss = { tab = 0 }) }
            }
        }
    }
    if (showRiskInfo) io.github.samolego.canta.ui.dialog.ExplainBadgesDialog { showRiskInfo = false }
    if (showSizeInfo) AlertDialog(onDismissRequest = { showSizeInfo = false },
        title = { Text(stringResource(R.string.app_apk_size_details)) },
        text = { Text(stringResource(R.string.app_apk_size_explanation)) },
        confirmButton = { TextButton(onClick = { showSizeInfo = false }) { Text(stringResource(R.string.ok)) } })
}
