package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DisabledByDefault
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.samolego.canta.R
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.ui.riskIcon
import io.github.samolego.canta.ui.riskColors

@Composable
fun RemovalBadge(type: RemovalRecommendation) {
    val colors = type.riskColors()
    AppBadge(
        label = stringResource(when (type) {
            RemovalRecommendation.RECOMMENDED -> R.string.risk_recommended
            RemovalRecommendation.ADVANCED -> R.string.risk_advanced
            RemovalRecommendation.EXPERT -> R.string.risk_expert
            RemovalRecommendation.UNSAFE -> R.string.risk_unsafe
            RemovalRecommendation.SYSTEM -> R.string.risk_system
        }),
        icon = type.riskIcon(),
        color = colors.container,
        contentColor = colors.onContainer,
    )
}

@Composable
fun SystemBadge() {
    RemovalBadge(type = RemovalRecommendation.SYSTEM)
}

@Composable
fun DisabledBadge() {
    AppBadge(
        label = stringResource(R.string.filter_disabled),
        icon = Icons.Default.DisabledByDefault,
        color = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
    )
}

@Composable
fun SuspendedBadge() = AppBadge(stringResource(R.string.suspended_badge), Icons.Default.PauseCircle,
    MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)

@Composable
fun CantaBadge() {
    AppBadge(
        label = stringResource(R.string.app_name),
        icon = Icons.Default.RestoreFromTrash,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

/** Tracker signatures a Cana scan found in the app's APK code; hidden until an app has been scanned. */
@Composable
fun TrackerBadge(count: Int) = AppBadge(
    label = pluralStringResource(R.plurals.tracker_badge, count, count),
    icon = Icons.Default.TrackChanges,
    color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
)

@Composable
private fun AppBadge(
    label: String,
    icon: ImageVector,
    color: Color,
    contentColor: Color,
) {
    Row(
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .padding(all = 4.dp)
            .background(
                color,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Icon(
            icon,
            tint = contentColor,
            modifier = Modifier
                .padding(start = 4.dp)
                .padding(vertical = 2.dp)
                .size(16.dp)
                .align(alignment = Alignment.CenterVertically),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            modifier = Modifier
                .padding(end = 8.dp)
                .align(alignment = Alignment.CenterVertically),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Preview
@Composable
fun BadgePreviews() {
    Column {
        for (removal in RemovalRecommendation.entries) {
            RemovalBadge(removal)
        }
        DisabledBadge()
    }
}
