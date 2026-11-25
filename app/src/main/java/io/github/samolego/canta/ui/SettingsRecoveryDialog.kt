package io.github.samolego.canta.ui

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.samolego.canta.R
import io.github.samolego.canta.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SettingsRecoveryDialog(settings: SettingsStore, closeApp: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.settings_recovery_title)
    var failed by remember { mutableStateOf(false) }
    val unlock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) scope.launch {
            try { settings.acknowledgeSettingsRecovery() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
        } else failed = true
    }
    AlertDialog(onDismissRequest = closeApp, title = { Text(title) },
        text = { Text(stringResource(if (failed) R.string.settings_recovery_auth_failed else R.string.settings_recovery_message)) },
        confirmButton = { TextButton(onClick = {
            @Suppress("DEPRECATION")
            val intent = context.getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent(title, null)
            if (intent == null) failed = true else unlock.launch(intent)
        }) { Text(stringResource(R.string.settings_recovery_action)) } },
        dismissButton = { TextButton(onClick = closeApp) { Text(stringResource(R.string.close)) } })
}
