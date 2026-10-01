package io.github.samolego.canta.ui.screen

import android.content.Intent
import android.os.Build
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.BuildConfig
import io.github.samolego.canta.R
import io.github.samolego.canta.util.openWebLink
import io.github.samolego.canta.ui.component.CanaWordmark
import io.github.samolego.canta.ui.component.IconClickButton
import io.github.samolego.canta.ui.component.SettingsItem
import io.github.samolego.canta.ui.component.SelfGrantSettings
import io.github.samolego.canta.ui.component.SectionHeader
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import io.github.samolego.canta.util.showBiometricPrompt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel,
    onVersionTap: () -> Unit,
) {
    val context = LocalContext.current
    val autoUpdateBloatList by settingsViewModel.autoUpdateBloatList.collectAsStateWithLifecycle()
    val unmeteredOnly by settingsViewModel.bloatUnmeteredOnly.collectAsStateWithLifecycle()
    val urlEditor by settingsViewModel.bloatListUrlEditorState.collectAsStateWithLifecycle()

    val allowUnsafe by settingsViewModel.allowUnsafeUninstall.collectAsStateWithLifecycle()
    val authEnabled by settingsViewModel.authEnabled.collectAsStateWithLifecycle()
    val useDynamicColor by settingsViewModel.useDynamicColor.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(io.github.samolego.canta.ui.LocalCanaSnackbar.current) },
        topBar = {
            ScreenTopBar(onNavigateBack, { Text(stringResource(R.string.settings)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SelfGrantSettings()

            // Auto-update bloat list
            SettingsItem(
                title = stringResource(R.string.auto_update_bloat_list),
                description = stringResource(R.string.auto_update_bloat_list_description),
                icon = Icons.Default.Update,
                isSwitch = true,
                checked = autoUpdateBloatList,
                onCheckedChange = {
                    settingsViewModel.saveAutoUpdateBloatList(it)
                }
            )

            SettingsItem(
                title = stringResource(R.string.bloat_unmetered_only),
                description = stringResource(R.string.bloat_unmetered_only_description),
                icon = Icons.Default.Update,
                isSwitch = true,
                checked = unmeteredOnly,
                onCheckedChange = settingsViewModel::saveBloatUnmeteredOnly,
            )

            SettingsItem(
                title = stringResource(R.string.require_auth_setting),
                description = stringResource(R.string.require_auth_setting_desc),
                icon = Icons.Default.Lock,
                isSwitch = true,
                checked = authEnabled,
                onCheckedChange = {
                    showBiometricPrompt(
                        context = context,
                        onSuccess = { settingsViewModel.saveAuthEnabled(it) },
                    )
                }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SectionHeader(stringResource(R.string.settings_appearance))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SettingsItem(
                title = stringResource(R.string.use_wallpaper_colors),
                description = stringResource(R.string.use_wallpaper_colors_description),
                icon = Icons.Default.Palette,
                isSwitch = true,
                checked = useDynamicColor,
                onCheckedChange = settingsViewModel::saveUseDynamicColor,
            )

            HorizontalDivider(modifier = Modifier.padding(16.dp))

            SectionHeader(stringResource(R.string.advanced_settings))
            SettingsItem(
                title = stringResource(R.string.allow_unsafe_removal),
                description = stringResource(R.string.allow_unsafe_removal_description),
                icon = Icons.Default.Close,
                isSwitch = true,
                checked = allowUnsafe,
                onCheckedChange = settingsViewModel::saveAllowUnsafeUninstalls,
            )
            SettingsItem(
                title = stringResource(R.string.bloat_list_url),
                description = urlEditor.currentUrl.ifEmpty { stringResource(R.string.maintenance_loading_url) },
                icon = Icons.Default.Link,
                onClick = if (urlEditor.loaded) settingsViewModel::beginBloatListUrlEdit else null,
            )

            // Spacer to push footer to bottom
            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            // App info footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CanaWordmark(style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = stringResource(R.string.brand_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                // App version
                Text(
                    text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable {
                        onVersionTap()
                    }
                )

                // App homepage
                Text(
                    text = "https://github.com/jordanwoodson/cana",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable {
                        context.openWebLink("https://github.com/jordanwoodson/cana")
                    }
                )
            }
        }
    }

    if (urlEditor.editing) {
        AlertDialog(
            onDismissRequest = settingsViewModel::cancelBloatListUrlEdit,
            title = { Text(stringResource(R.string.bloat_list_url)) },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.maintenance_url_description))
                    OutlinedTextField(
                        value = urlEditor.draft,
                        onValueChange = settingsViewModel::editBloatListUrl,
                        label = { Text(stringResource(R.string.bloat_list_url)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                        isError = urlEditor.invalid,
                        enabled = !urlEditor.saving,
                        supportingText = { if (urlEditor.invalid) Text(stringResource(R.string.maintenance_url_invalid)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = settingsViewModel::resetBloatListUrlDraft, enabled = !urlEditor.saving) {
                        Text(stringResource(R.string.maintenance_url_default))
                    }
                    if (urlEditor.saveFailed) Text(stringResource(R.string.maintenance_url_save_failed), color = MaterialTheme.colorScheme.error)
                    if (urlEditor.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = settingsViewModel::saveBloatListUrlDraft, enabled = !urlEditor.saving) {
                Text(stringResource(R.string.save))
            } },
            dismissButton = { TextButton(onClick = settingsViewModel::cancelBloatListUrlEdit, enabled = !urlEditor.saving) {
                Text(stringResource(R.string.cancel))
            } },
        )
    }
}
