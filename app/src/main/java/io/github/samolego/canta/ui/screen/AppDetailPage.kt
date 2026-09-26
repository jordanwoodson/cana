package io.github.samolego.canta.ui.screen

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.format.Formatter
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.*
import io.github.samolego.canta.ui.component.*
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.util.LogUtils
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
    val state = services.inventory.state(target.userId)
    val app = state.apps.find { it.packageName == target.packageName }
    LaunchedEffect(target) { if (!state.loaded || state.stale) services.inventory.snapshot(target.userId) }
    Scaffold(topBar = { TopAppBar(
        title = { Column {
            Text(app?.name ?: target.packageName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.action_user, target.userId), style = MaterialTheme.typography.labelMedium)
        } },
        navigationIcon = { IconClickButton(onClick = onNavigateBack, icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
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
                tab == 0 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectionContainer { Text(target.packageName) }
                    TextButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Package", target.packageName))) } }) {
                        Text(stringResource(R.string.copy_package_name_to_clipboard))
                    }
                    Text(stringResource(R.string.app_version_details, app.versionName ?: "—", app.versionCode))
                    Text(stringResource(R.string.app_apk_size, Formatter.formatFileSize(context, app.apkSizeBytes)))
                    FlowRow {
                        app.removalInfo?.let { RemovalBadge(it) }
                        if (app.isSystemApp) SystemBadge()
                        if (app.isDisabled) DisabledBadge()
                        if (app.isSuspended) SuspendedBadge()
                    }
                    Text(stringResource(if (app.isUninstalled) R.string.tab_removed else R.string.tab_installed))
                    app.lastUsed?.let { lastUsed -> Text(if (lastUsed == 0L) stringResource(R.string.last_used_no_record)
                        else stringResource(R.string.last_used_date, java.text.DateFormat.getDateInstance().format(java.util.Date(lastUsed)))) }
                    val description = app.description
                    if (description != null) {
                        val color = MaterialTheme.colorScheme.onSurface.toArgb()
                        AndroidView(factory = { TextView(it).apply { setTextIsSelectable(true); movementMethod = LinkMovementMethod.getInstance(); textSize = 16f } },
                            update = { it.text = Html.fromHtml(description, Html.FROM_HTML_MODE_COMPACT); it.setTextColor(color) },
                            modifier = Modifier.fillMaxWidth())
                    } else Text(stringResource(R.string.no_description_available))
                    app.bloatData?.suggestions?.takeIf { it.isNotEmpty() }?.let { Text(stringResource(R.string.app_suggestions, it.toString())) }
                    HorizontalDivider()
                    val actions = if (app.isUninstalled) buildList {
                        add(PackageAction.REINSTALL)
                        if (app.isUpdatedSystemApp) add(PackageAction.REMOVE_UPDATES)
                    } else listOf(if (app.isDisabled) PackageAction.ENABLE else PackageAction.DISABLE,
                        if (app.isSuspended) PackageAction.UNSUSPEND else PackageAction.SUSPEND,
                        PackageAction.UNINSTALL_KEEP_DATA, PackageAction.UNINSTALL)
                    actions.forEach { action -> OutlinedButton(onClick = { onAction(action) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.app_review_action, stringResource(action.label)))
                    } }
                    if (!app.isUninstalled) TextButton(onClick = { scope.launch {
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
                    } }) { Text(stringResource(R.string.app_settings)) }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                app.isUninstalled -> Text(stringResource(R.string.app_removed_sections), Modifier.padding(24.dp))
                !ShizukuPermission.isCantaAuthorized() -> Text(stringResource(R.string.inventory_disconnected), Modifier.padding(24.dp))
                else -> Box(Modifier.padding(16.dp)) { io.github.samolego.canta.ui.dialog.ComponentsDialog(target.packageName, target.userId, embedded = true, onDismiss = { tab = 0 }) }
            }
        }
    }
}
