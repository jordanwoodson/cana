package io.github.samolego.canta.ui.component

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.util.openWebLink
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ops.TrackerScanProgress
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.util.apps.AppInfo
import kotlinx.coroutines.CancellationException

/** Tracker signatures found in an app's APK code; scanned when first shown and cached per version. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackerSummary(app: AppInfo) {
    val repository = CanaServices.getInstance().trackerScans
    val scan = repository.result(app)
    var request by remember(app.packageName, app.versionCode, app.apkIdentity, repository.generation) { mutableIntStateOf(0) }
    var busy by remember(app.packageName, app.versionCode, app.apkIdentity, repository.generation, request) { mutableStateOf(false) }
    var failure by remember(app.packageName, app.versionCode, app.apkIdentity, repository.generation, request) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(app.packageName, app.versionCode, app.apkIdentity, repository.generation, request) {
        if (scan == null || request > 0) {
            busy = true
            failure = null
            try { repository.scan(app, force = request > 0) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = e.message ?: e.toString() }
            finally { busy = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.trackers_title), style = MaterialTheme.typography.titleSmall)
        val error = failure ?: scan?.error
        when {
            busy -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.tracker_scan_running), style = MaterialTheme.typography.bodySmall)
            }
            error != null -> Text(stringResource(R.string.tracker_scan_failed, error), color = MaterialTheme.colorScheme.error)
            scan != null -> {
                Text(pluralStringResource(R.plurals.tracker_count, scan.trackers.size, scan.trackers.size))
                if (scan.trackers.isNotEmpty()) Text(scan.trackers.joinToString(" · "), color = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.tracker_scan_classes, scan.classCount, scan.apkCount), style = MaterialTheme.typography.labelSmall)
            }
            else -> Text(stringResource(R.string.tracker_not_scanned), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.tracker_scan_description), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.tracker_match_description), style = MaterialTheme.typography.bodySmall)
        repository.catalog?.let {
            Text(stringResource(R.string.tracker_active_source, it.source), style = MaterialTheme.typography.labelSmall)
            it.error?.let { error -> Text(stringResource(R.string.tracker_fallback, error), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall) }
        }
        Text(stringResource(R.string.tracker_attribution), style = MaterialTheme.typography.labelSmall)
        FlowRow {
            TextButton(enabled = !busy, onClick = { request++ }) { Text(stringResource(R.string.tracker_rescan_action)) }
            TextButton(onClick = {
                context.openWebLink("https://reports.exodus-privacy.eu.org/en/reports/${Uri.encode(app.packageName)}/latest/")
            }) {
                Text(stringResource(R.string.tracker_exodus_report))
            }
        }
    }
}

/** Whole-profile scan controls for the Filters sheet: how many installed apps have a result, and a start button. */
@Composable
fun TrackerScanControls(model: AppListViewModel) {
    val repository = CanaServices.getInstance().trackerScans
    val context = LocalContext.current
    val progress = repository.progress
    val installed = model.allApps.filter { !it.isUninstalled }
    val scanned = installed.count { app -> repository.count(app) != null }
    if (progress != null) TrackerScanProgressRow(progress, onCancel = repository::cancelScanAll)
    else Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.tracker_scanned_count, scanned, installed.size), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = installed.isNotEmpty(), onClick = { startTrackerScan(context, model) }) { Text(stringResource(R.string.tracker_scan_all)) }
    }
}

@Composable
fun TrackerScanProgressRow(progress: TrackerScanProgress, modifier: Modifier = Modifier, onCancel: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tracker_scan_progress, progress.done, progress.total), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (onCancel != null) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/** Scans every installed app of the selected profile in the background and reports the outcome globally. */
fun startTrackerScan(context: Context, model: AppListViewModel) {
    val appContext = context.applicationContext
    val services = CanaServices.getInstance()
    services.trackerScans.scanAll(model.allApps, model.selectedUserId) { scanned, withTrackers, failed ->
        services.uiMessages.post(appContext.getString(R.string.tracker_scan_summary, withTrackers, scanned, failed))
    }
}
