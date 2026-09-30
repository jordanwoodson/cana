package io.github.samolego.canta.ui.dialog

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.R
import io.github.samolego.canta.SHIZUKU_PACKAGE_NAME
import io.github.samolego.canta.util.shizuku.AuthorizationState
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import io.github.samolego.canta.util.shizuku.ShizukuStatus

private const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"

@Composable
fun ShizukuRequirementDialog(onClose: (shouldProceed: Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val authorization by ShizukuPermission.state.collectAsStateWithLifecycle()
    val latestClose by rememberUpdatedState(onClose)
    var status by remember { mutableStateOf(ShizukuPermission.checkShizukuActive(context.packageManager)) }
    var usbDebugging by remember { mutableStateOf(false) }
    var launchFailed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        fun refresh() {
            ShizukuPermission.refresh()
            status = ShizukuPermission.checkShizukuActive(context.packageManager)
            usbDebugging = runCatching {
                Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
            }.getOrDefault(false)
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(authorization) {
        status = ShizukuPermission.checkShizukuActive(context.packageManager)
        if (authorization == AuthorizationState.GRANTED) latestClose(true)
    }

    fun launch(intent: Intent?) {
        launchFailed = intent == null || runCatching { context.startActivity(intent) }.isFailure
    }
    ShizukuRequirementContent(
        authorization = authorization,
        status = status,
        usbDebugging = usbDebugging,
        launchFailed = launchFailed,
        onAuthorize = { launchFailed = false; ShizukuPermission.requestShizukuPermission() },
        onOpenManager = { launch(context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)) },
        onInstall = { launch(Intent(Intent.ACTION_VIEW, SHIZUKU_DOWNLOAD_URL.toUri())) },
        onOpenDeveloperSettings = { launch(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
        onClose = { latestClose(false) },
    )
}

@Composable
internal fun ShizukuRequirementContent(
    authorization: AuthorizationState,
    status: ShizukuStatus,
    usbDebugging: Boolean,
    launchFailed: Boolean = false,
    onAuthorize: () -> Unit,
    onOpenManager: () -> Unit,
    onInstall: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val active = authorization !in setOf(AuthorizationState.DISCONNECTED, AuthorizationState.UNSUPPORTED)
    val waiting = authorization == AuthorizationState.REQUESTING
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.shizuku_required)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.shizuku_requirement_description))
                RequirementItem(stringResource(R.string.install_shizuku), status != ShizukuStatus.NOT_INSTALLED,
                    onActionClick = onInstall)
                RequirementItem(stringResource(R.string.start_shizuku_service), active,
                    enabled = status != ShizukuStatus.NOT_INSTALLED, onActionClick = onOpenManager)
                RequirementItem(stringResource(R.string.grant_shizuku_permission_to_canta), authorization == AuthorizationState.GRANTED,
                    enabled = active && !waiting && authorization != AuthorizationState.BLOCKED,
                    onActionClick = onAuthorize)
                val message = when (authorization) {
                    AuthorizationState.DISCONNECTED -> R.string.shizuku_connect_service
                    AuthorizationState.UNSUPPORTED -> R.string.shizuku_update_required
                    AuthorizationState.REQUESTING -> R.string.shizuku_waiting
                    AuthorizationState.DENIED -> R.string.shizuku_denied
                    AuthorizationState.BLOCKED -> R.string.shizuku_blocked
                    AuthorizationState.ERROR -> R.string.shizuku_request_failed
                    AuthorizationState.TIMED_OUT -> R.string.shizuku_request_timeout
                    else -> null
                }
                if (waiting) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                if (authorization in setOf(AuthorizationState.BLOCKED, AuthorizationState.DENIED,
                        AuthorizationState.TIMED_OUT, AuthorizationState.ERROR, AuthorizationState.UNSUPPORTED)) {
                    TextButton(onClick = onOpenManager) { Text(stringResource(R.string.shizuku_open_manager)) }
                }
                HorizontalDivider()
                Text(stringResource(R.string.shizuku_usb_optional), style = MaterialTheme.typography.bodySmall)
                RequirementItem(stringResource(R.string.enable_usb_debugging), usbDebugging,
                    onActionClick = onOpenDeveloperSettings)
                if (launchFailed) Text(stringResource(R.string.shizuku_launch_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun RequirementItem(text: String, isCompleted: Boolean, enabled: Boolean = true, onActionClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (isCompleted) Icons.Default.Check else Icons.Default.Circle, contentDescription = null,
            tint = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (!isCompleted) IconButton(onClick = onActionClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Default.ArrowForward, contentDescription = text)
        }
    }
}
