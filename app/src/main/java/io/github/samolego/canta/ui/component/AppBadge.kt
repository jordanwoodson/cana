package io.github.samolego.canta.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DisabledByDefault
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.samolego.canta.R
import io.github.samolego.canta.util.RemovalRecommendation

@Composable
fun RemovalBadge(type: RemovalRecommendation) = RiskChip(type)

@Composable
fun SystemBadge() = RiskChip(RemovalRecommendation.SYSTEM)

@Composable
fun DisabledBadge() = StatusChip(stringResource(R.string.filter_disabled), Icons.Default.DisabledByDefault)

@Composable
fun SuspendedBadge() = StatusChip(stringResource(R.string.suspended_badge), Icons.Default.PauseCircle)

@Composable
fun CantaBadge() = StatusChip(stringResource(R.string.app_name), Icons.Default.RestoreFromTrash, StatusTone.ERROR)

/** Tracker signatures a Cana scan found in the app's APK code; hidden until an app has been scanned. */
@Composable
fun TrackerBadge(count: Int) = StatusChip(
    pluralStringResource(R.plurals.tracker_badge, count, count), Icons.Default.TrackChanges,
)
