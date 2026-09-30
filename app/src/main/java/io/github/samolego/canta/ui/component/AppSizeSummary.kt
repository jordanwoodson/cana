package io.github.samolego.canta.ui.component

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.samolego.canta.R
import io.github.samolego.canta.util.apps.AppInfo

@Composable
fun AppSizeSummary(app: AppInfo) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.app_size_unavailable)
    fun format(bytes: Long) = if (bytes > 0) Formatter.formatFileSize(context, bytes) else unavailable
    Column {
        Text(stringResource(R.string.app_apk_size, format(app.apkSizeBytes)),
            style = MaterialTheme.typography.bodySmall)
        if (app.isUpdatedSystemApp) Text(stringResource(R.string.app_update_apk_size, format(app.updateSizeBytes)),
            style = MaterialTheme.typography.bodySmall)
    }
}
