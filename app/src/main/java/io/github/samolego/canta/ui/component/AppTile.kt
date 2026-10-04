package io.github.samolego.canta.ui.component

import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.samolego.canta.R
import io.github.samolego.canta.packageName
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.InstallData
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.apps.AppInfo
import io.github.samolego.canta.util.apps.UserProfile

/** Compact row. A long press selects without opening details; all package actions remain elsewhere. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppTile(
    modifier: Modifier = Modifier,
    appInfo: AppInfo,
    isSelected: Boolean,
    enabled: Boolean = true,
    userId: Int = UserProfile.currentUserId,
    onCheckChanged: (Boolean) -> Unit,
    onShowDialog: () -> Unit,
    trackerCount: Int? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val contentAlpha = if (enabled) 1f else 0.38f
    val sizeBytes = when {
        appInfo.apkSizeBytes > 0 -> appInfo.apkSizeBytes
        appInfo.isUninstalled && appInfo.isUpdatedSystemApp && appInfo.updateSizeBytes > 0 -> appInfo.updateSizeBytes
        else -> 0L
    }
    val sizeLabel = if (sizeBytes > 0) Formatter.formatShortFileSize(context, sizeBytes)
        else stringResource(R.string.app_size_unavailable)
    val badgeLabels = listOfNotNull(
        appInfo.shownRisk?.let { risk -> stringResource(when (risk) {
            RemovalRecommendation.RECOMMENDED -> R.string.risk_recommended
            RemovalRecommendation.ADVANCED -> R.string.risk_advanced
            RemovalRecommendation.EXPERT -> R.string.risk_expert
            RemovalRecommendation.UNSAFE -> R.string.risk_unsafe
            RemovalRecommendation.SYSTEM -> R.string.risk_system
        }) },
        if (appInfo.isDisabled) stringResource(R.string.filter_disabled) else null,
        if (appInfo.isSuspended) stringResource(R.string.suspended_badge) else null,
        if (appInfo.isUninstalled && appInfo.isUpdatedSystemApp) stringResource(R.string.leftover_update_chip) else null,
        trackerCount?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.tracker_badge, it, it) },
        if (appInfo.packageName == packageName) stringResource(R.string.app_name) else null,
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Chip padding (20dp), icon (14dp), and the icon/text gap (4dp).
    val widestBadge = badgeLabels.maxOfOrNull {
        textMeasurer.measure(it, MaterialTheme.typography.labelMedium, softWrap = false).size.width
    }?.plus(with(density) { 38.dp.roundToPx() }) ?: 0
    Box(modifier.fillMaxWidth().heightIn(min = 88.dp)) {
        ListItem(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                enabled = enabled,
                onClick = onShowDialog,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheckChanged(!isSelected)
                },
            ),
            colors = ListItemDefaults.colors(
                containerColor = if (isSelected) scheme.secondaryContainer else scheme.surface,
            ),
            leadingContent = {
                Box(Modifier.alpha(contentAlpha)) { AppIconImage(appInfo, userId, size = 40.dp) }
            },
            headlineContent = {
                Text(appInfo.name, modifier = Modifier.alpha(contentAlpha),
                    style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Column(Modifier.alpha(contentAlpha)) {
                    Text(appInfo.packageName, style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Layout(modifier = Modifier.fillMaxWidth().heightIn(min = 26.dp), content = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            appInfo.shownRisk?.let { RiskChip(it) }
                            if (appInfo.isDisabled) DisabledBadge()
                            if (appInfo.isSuspended) SuspendedBadge()
                            if (appInfo.isUninstalled && appInfo.isUpdatedSystemApp) StatusChip(
                                stringResource(R.string.leftover_update_chip), Icons.Default.Update, StatusTone.WARNING)
                            if (trackerCount != null && trackerCount > 0) TrackerBadge(trackerCount)
                            if (appInfo.packageName == packageName) CantaBadge()
                        }
                        Text(sizeLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                            maxLines = 1)
                    }) { measurables, constraints ->
                        val gap = 4.dp.roundToPx()
                        val size = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
                        val stacked = widestBadge + size.width + gap > constraints.maxWidth
                        val badges = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0,
                            maxWidth = if (stacked) constraints.maxWidth else (constraints.maxWidth - size.width - gap).coerceAtLeast(0)))
                        val contentHeight = if (stacked) badges.height + gap + size.height else maxOf(badges.height, size.height)
                        val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
                        layout(constraints.maxWidth, height) {
                            if (stacked) {
                                badges.placeRelative(0, 0)
                                size.placeRelative(0, badges.height + gap)
                            } else {
                                badges.placeRelative(0, (height - badges.height) / 2)
                                size.placeRelative(constraints.maxWidth - size.width, (height - size.height) / 2)
                            }
                        }
                    }
                }
            },
            trailingContent = {
                val selectionLabel = stringResource(
                    if (isSelected) R.string.app_deselect_description else R.string.app_select_description, appInfo.name)
                Checkbox(
                    modifier = Modifier.semantics { contentDescription = selectionLabel },
                    checked = isSelected,
                    onCheckedChange = onCheckChanged,
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(checkedColor = scheme.primary,
                        uncheckedColor = scheme.onSurfaceVariant),
                )
            },
        )
        HorizontalDivider(Modifier.align(Alignment.BottomCenter).padding(start = 72.dp),
            thickness = 1.dp, color = scheme.outlineVariant)
    }
}

@Composable
fun AppIconImage(appInfo: AppInfo, userId: Int = UserProfile.currentUserId, size: Dp = 40.dp) {
    val context = LocalContext.current
    val icon = remember(appInfo.packageName, appInfo.applicationInfo, userId) {
        try {
            val raw = appInfo.applicationInfo?.let { context.packageManager.getApplicationIcon(it) }
                ?: context.packageManager.getApplicationIcon(appInfo.packageName)
            if (userId != UserProfile.currentUserId)
                context.packageManager.getUserBadgedIcon(raw, UserHandle.getUserHandleForUid(userId * 100_000))
            else raw
        } catch (e: Exception) {
            LogUtils.w("AppTile", "Cannot load ${appInfo.packageName} icon: ${e.message}")
            null
        }
    }
    if (icon != null) AppIconImage(icon, contentDescription = null, size = size)
    else Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(size))
}

@Composable
fun AppIconImage(appIconImage: Drawable, contentDescription: String?, size: Dp = 40.dp) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(appIconImage).crossfade(true).build(),
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
    )
}

@Preview(name = "Compact app row Dracula", showBackground = true, backgroundColor = 0xFF282A36)
@Composable
private fun CompactAppRowPreview() {
    CantaTheme {
        AppTile(
            appInfo = AppInfo("Android Keyboard (AOSP)", "com.android.inputmethod.latin", "1", 1,
                isSystemApp = true, isUninstalled = false, isDisabled = false,
                bloatData = BloatData(InstallData.OEM, "Keyboard", RemovalRecommendation.ADVANCED),
                apkSizeBytes = 19_000_000),
            isSelected = false,
            onCheckChanged = {},
            onShowDialog = {},
        )
    }
}
