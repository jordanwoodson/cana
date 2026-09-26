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
import androidx.compose.ui.res.stringResource
import io.github.samolego.canta.R
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.util.RemovalRecommendation

@Composable
fun RemovalBadge(type: RemovalRecommendation) {
    AppBadge(
        label = stringResource(when (type) {
            RemovalRecommendation.RECOMMENDED -> R.string.risk_recommended
            RemovalRecommendation.ADVANCED -> R.string.risk_advanced
            RemovalRecommendation.EXPERT -> R.string.risk_expert
            RemovalRecommendation.UNSAFE -> R.string.risk_unsafe
            RemovalRecommendation.SYSTEM -> R.string.risk_system
        }),
        icon = type.icon,
        color = type.badgeColor
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
    )
}

@Composable
fun SuspendedBadge() = AppBadge(stringResource(R.string.suspended_badge), Icons.Default.PauseCircle, MaterialTheme.colorScheme.secondary)

@Composable
fun CantaBadge() {
    AppBadge(
        label = stringResource(R.string.app_name),
        icon = Icons.Default.RestoreFromTrash,
        color = Color.Red.copy(alpha = 0.7f),
    )
}

@Composable
private fun AppBadge(
    label: String,
    icon: ImageVector,
    color: Color,
) {
    val contrastColor = color.getContrastColor()
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
            tint = contrastColor,
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
            color = contrastColor,
        )
    }
}

private fun Color.getContrastColor(): Color {
    return if (luminance() > 0.179) Color.Black else Color.White
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
