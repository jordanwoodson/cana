package io.github.samolego.canta.ui.screen

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.R
import io.github.samolego.canta.SHIZUKU_PACKAGE_NAME
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ui.dialog.ShizukuRequirementContent
import io.github.samolego.canta.ui.menu.displayName
import io.github.samolego.canta.ui.menu.icon
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.util.apps.UserProfile
import io.github.samolego.canta.util.shizuku.AuthorizationState
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomePage(appListViewModel: AppListViewModel, onExit: () -> Unit, onFinished: suspend () -> Unit) {
    val context = LocalContext.current
    val saveFailed = stringResource(R.string.welcome_save_failed)
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val authorization by ShizukuPermission.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(1) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var profiles by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var selectedUserId by rememberSaveable { mutableIntStateOf(UserProfile.currentUserId) }
    var status by remember { mutableStateOf(ShizukuPermission.checkShizukuActive(context.packageManager)) }
    var usbDebugging by remember { mutableStateOf(false) }
    var launchFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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

    fun launch(intent: Intent?) {
        launchFailed = intent == null || runCatching { context.startActivity(intent) }.isFailure
    }
    suspend fun complete() {
        try { onFinished() } catch (e: Exception) { error = e.message ?: saveFailed }
    }
    fun finish() {
        busy = true
        scope.launch { try { complete() } finally { busy = false } }
    }

    LaunchedEffect(step, authorization) {
        if (step == 3 && authorization == AuthorizationState.GRANTED && profiles.isEmpty()) {
            if (runCatching { CanaServices.getInstance().profiles.refresh() }.getOrDefault(false))
                profiles = CanaServices.getInstance().profiles.profiles.value
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.welcome_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(when (step) {
                1 -> R.string.welcome_step_one
                2 -> R.string.welcome_step_two
                else -> R.string.welcome_step_three
            }), style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    1 -> {
                        Text(stringResource(R.string.welcome_bullet_one))
                        Text(stringResource(R.string.welcome_bullet_two))
                        Text(stringResource(R.string.welcome_bullet_three))
                        HorizontalDivider()
                        Text(stringResource(R.string.no_warranty_content))
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(value = acknowledged, role = Role.Checkbox) { acknowledged = it },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(acknowledged, onCheckedChange = null)
                            Text(stringResource(R.string.welcome_acknowledge))
                        }
                    }
                    2 -> ShizukuRequirementContent(
                        authorization = authorization, status = status, usbDebugging = usbDebugging,
                        launchFailed = launchFailed, inline = true,
                        onAuthorize = { launchFailed = false; ShizukuPermission.requestShizukuPermission() },
                        onOpenManager = { launch(context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)) },
                        onInstall = { launch(Intent(Intent.ACTION_VIEW, "https://shizuku.rikka.app/download/".toUri())) },
                        onOpenDeveloperSettings = { launch(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                        onClose = {},
                    )
                    else -> {
                        Text(stringResource(R.string.welcome_profile_description))
                        profiles.forEach { profile ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = selectedUserId == profile.id, role = Role.RadioButton) { selectedUserId = profile.id }
                                .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedUserId == profile.id, onClick = null)
                                Icon(profile.icon(), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(profile.displayName())
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (step == 3) Arrangement.End else Arrangement.SpaceBetween) {
                when (step) {
                    1 -> {
                        TextButton(onClick = onExit) { Text(stringResource(R.string.welcome_exit)) }
                        Button(enabled = acknowledged, onClick = { step = 2 }) { Text(stringResource(R.string.welcome_continue)) }
                    }
                    2 -> {
                        TextButton(enabled = !busy, onClick = ::finish) { Text(stringResource(R.string.welcome_skip)) }
                        Button(enabled = !busy && authorization == AuthorizationState.GRANTED, onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    if (runCatching { CanaServices.getInstance().profiles.refresh() }.getOrDefault(false)) {
                                        profiles = CanaServices.getInstance().profiles.profiles.value
                                    }
                                    if (profiles.size > 1) step = 3 else complete()
                                } finally { busy = false }
                            }
                        }) { Text(stringResource(R.string.welcome_continue)) }
                    }
                    else -> Button(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                profiles.firstOrNull { it.id == selectedUserId }?.let { profile ->
                                    appListViewModel.selectUser(profile, context.packageManager, context)
                                }
                                complete()
                            } finally { busy = false }
                        }
                    }) { Text(stringResource(R.string.welcome_continue)) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
