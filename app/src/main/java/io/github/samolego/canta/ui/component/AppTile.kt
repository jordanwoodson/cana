package io.github.samolego.canta.ui.component

import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
@OptIn(ExperimentalFoundationApi::class)
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
    Box(modifier.fillMaxWidth().height(88.dp)) {
        ListItem(
            modifier = Modifier.fillMaxSize().combinedClickable(
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
                    Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clipToBounds(), horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            appInfo.bloatData?.verdict?.let { VerdictBadge(it) }
                            appInfo.shownRisk?.let { RiskChip(it) }
                            if (appInfo.isDisabled) DisabledBadge()
                            if (appInfo.isSuspended) SuspendedBadge()
                            if (appInfo.isUninstalled && appInfo.isUpdatedSystemApp) StatusChip(
                                stringResource(R.string.leftover_update_chip), Icons.Default.Update, StatusTone.WARNING)
                            if (trackerCount != null && trackerCount > 0) TrackerBadge(trackerCount)
                            if (appInfo.packageName == packageName) CantaBadge()
                        }
                        Text(sizeLabel, modifier = Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                            maxLines = 1)
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

@Preview(name = "Compact app row light", showBackground = true)
@Composable
private fun CompactAppRowLightPreview() = CompactAppRowPreview(false)

@Preview(name = "Compact app row dark", showBackground = true)
@Composable
private fun CompactAppRowDarkPreview() = CompactAppRowPreview(true)

@Composable
private fun CompactAppRowPreview(dark: Boolean) {
    CantaTheme(darkTheme = dark) {
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
