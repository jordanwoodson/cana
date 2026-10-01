package io.github.samolego.canta.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DisabledByDefault
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.samolego.canta.R
import io.github.samolego.canta.util.CanaVerdict
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

/** Cana's verdict overlay advises uninstalling this app for privacy or performance. */
@Composable
fun VerdictBadge() = StatusChip(stringResource(R.string.verdict_badge), Icons.Default.DeleteSweep, StatusTone.WARNING)

/** Cana's keep-or-uninstall advice, with what uninstalling gains and why. */
@Composable
fun VerdictCard(verdict: CanaVerdict, modifier: Modifier = Modifier) {
    val gains = verdict.benefits.map { stringResource(if (it == CanaVerdict.PRIVACY) R.string.verdict_privacy else R.string.verdict_performance) }
    CanaBanner(
        if (verdict.uninstall) Icons.Default.DeleteSweep else Icons.Default.Verified,
        stringResource(if (verdict.uninstall) R.string.verdict_uninstall_title else R.string.verdict_keep_title),
        if (verdict.uninstall) StatusTone.WARNING else StatusTone.NEUTRAL,
        modifier,
        details = if (verdict.reason == null && gains.isEmpty()) null else {
            {
                verdict.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (gains.isNotEmpty()) Text(stringResource(R.string.verdict_gains, gains.joinToString()),
                    style = MaterialTheme.typography.labelMedium)
            }
        },
    )
}
