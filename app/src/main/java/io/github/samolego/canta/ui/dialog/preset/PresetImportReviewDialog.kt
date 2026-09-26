package io.github.samolego.canta.ui.dialog.preset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.data.PresetImportResult
import io.github.samolego.canta.ops.PresetImportReview
import io.github.samolego.canta.ops.PresetProfilePreview
import io.github.samolego.canta.util.LockdownSettings

@Composable
fun PresetImportReviewDialog(
    review: PresetImportReview,
    checking: Boolean,
    saving: Boolean,
    error: PresetImportResult?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.preset_review_import)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(review.preset.name, style = MaterialTheme.typography.titleMedium)
                if (review.preset.description.isNotBlank()) Text(review.preset.description)
                Text(stringResource(R.string.preset_import_version, review.preset.version), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.preset_import_review_description))
                Text(if (review.previous == null) stringResource(R.string.preset_import_new)
                    else stringResource(R.string.preset_import_existing, review.previous.name))
                review.previous?.let { previous ->
                    if (previous.name != review.preset.name) Text(stringResource(R.string.preset_import_metadata_before, previous.name))
                    if (previous.description != review.preset.description && previous.description.isNotBlank())
                        Text(stringResource(R.string.preset_import_metadata_before, previous.description))
                }
                HorizontalDivider()
                Text(stringResource(R.string.preset_import_changes), style = MaterialTheme.typography.titleSmall)
                review.diff.addedRemovals.forEach { Text(stringResource(R.string.preset_import_added_removal, it)) }
                review.diff.removedRemovals.forEach { Text(stringResource(R.string.preset_import_removed_removal, it)) }
                if (review.diff.privacyChanges.isNotEmpty()) Text(stringResource(R.string.preset_import_privacy_changes), style = MaterialTheme.typography.titleSmall)
                review.diff.privacyChanges.forEach { change ->
                    Text(change.packageName)
                    Text(stringResource(R.string.preset_import_privacy_before, privacyActions(change.before)), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.preset_import_privacy_after, privacyActions(change.after)), style = MaterialTheme.typography.bodySmall)
                }
                if (review.diff.addedRemovals.isEmpty() && review.diff.removedRemovals.isEmpty() && review.diff.privacyChanges.isEmpty())
                    Text(stringResource(R.string.preset_import_no_action_changes))
                if (review.diff.profileKindChanged) Text(stringResource(R.string.preset_import_profile_change,
                    profileKindLabel(review.previous?.profileKind), profileKindLabel(review.preset.profileKind)))
                if (review.diff.removedRemovals.isNotEmpty() || review.diff.privacyChanges.any { it.before != null })
                    Text(stringResource(R.string.preset_import_removal_explanation), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text(stringResource(R.string.preset_import_actions), style = MaterialTheme.typography.titleSmall)
                PresetActionsContent(review.preset)
                if (checking) {
                    LinearProgressIndicator()
                    Text(stringResource(R.string.preset_import_checking))
                } else PresetProfilePreviewContent(review.profiles)
                if (saving) LinearProgressIndicator()
                error?.let { Text(stringResource(if (it == PresetImportResult.REVIEW_CHANGED) R.string.preset_import_changed else R.string.import_failed),
                    color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !saving && !checking) {
            Text(stringResource(if (review.previous == null) R.string.import_button else R.string.preset_import_update))
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun PresetActionsContent(preset: io.github.samolego.canta.util.CantaPresetData) {
    preset.profileKind?.let { Text(stringResource(R.string.preset_profile_hint, profileKindLabel(it))) }
    preset.apps.sorted().forEach { Text(stringResource(R.string.preset_remove_entry, it)) }
    preset.lockdown.sortedBy { it.packageName }.forEach {
        Text(it.packageName)
        Text(privacyActions(it), style = MaterialTheme.typography.bodySmall)
    }
    if (preset.apps.isEmpty() && preset.lockdown.isEmpty()) Text(stringResource(R.string.preset_import_empty))
}

/** Shared read-only availability/mismatch section for import and apply preflight. */
@Composable
fun PresetProfilePreviewContent(profiles: List<PresetProfilePreview>) {
    profiles.forEach { profile ->
        HorizontalDivider()
        Text(stringResource(R.string.profile_subtitle, profile.name ?: profile.kind?.let { profileKindLabel(it) }
            ?: stringResource(R.string.profile_other), profile.userId),
            style = MaterialTheme.typography.titleSmall)
        if (profile.profileMismatch) Text(stringResource(R.string.preset_profile_mismatch), color = MaterialTheme.colorScheme.error)
        if (profile.profileKindUnknown) Text(stringResource(R.string.preset_import_profile_unknown))
        if (!profile.inventoryAvailable) Text(stringResource(R.string.preset_import_inventory_unavailable))
        else {
            profile.missingPackages.forEach { Text(stringResource(R.string.preset_import_missing, it)) }
            profile.alreadyRemovedPackages.forEach { Text(stringResource(R.string.preset_import_removed, it)) }
            if (profile.missingPackages.isEmpty() && profile.alreadyRemovedPackages.isEmpty()) Text(stringResource(R.string.preset_import_inventory_checked))
        }
    }
}

@Composable
private fun privacyActions(settings: LockdownSettings?): String = buildList {
    if (settings?.revokePermissions == true) add(stringResource(R.string.preset_action_permissions))
    if (settings?.restrictBackground == true) add(stringResource(R.string.preset_action_background))
    if (settings?.denyMetered == true) add(stringResource(R.string.preset_action_metered))
    if (settings?.blockNetwork == true) add(stringResource(R.string.preset_action_network))
}.joinToString().ifEmpty { stringResource(R.string.preset_import_no_privacy) }

@Composable
private fun profileKindLabel(kind: String?): String = when (kind) {
    null -> stringResource(R.string.preset_import_no_profile)
    "PERSONAL" -> stringResource(R.string.profile_personal)
    "WORK" -> stringResource(R.string.profile_work)
    "CLONE" -> stringResource(R.string.profile_clone)
    "PRIVATE" -> stringResource(R.string.profile_private)
    "USER" -> stringResource(R.string.profile_user)
    "OTHER" -> stringResource(R.string.profile_other)
    else -> kind
}
