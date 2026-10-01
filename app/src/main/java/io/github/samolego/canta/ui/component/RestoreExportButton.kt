package io.github.samolego.canta.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ops.RestoreScript
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RestoreExportButton(iconOnly: Boolean = false) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-shellscript")) { uri ->
        if (uri != null) scope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    val script = RestoreScript.generate(CanaServices.getInstance().history.allRecords())
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(script) }
                }
                resources.getString(R.string.restore_export_saved)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { resources.getString(R.string.restore_export_failed, e.message.orEmpty()) }
            CanaServices.getInstance().uiMessages.post(message)
        }
    }
    if (iconOnly) IconButton(onClick = { picker.launch("cana-restore.sh") }) {
        Icon(Icons.Default.SaveAlt, contentDescription = stringResource(R.string.restore_export))
    } else TextButton(onClick = { picker.launch("cana-restore.sh") }) { Text(stringResource(R.string.restore_export)) }
}
