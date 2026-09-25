package io.github.samolego.canta.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.util.apps.UserProfile

@Composable
fun ProfilesMenu(
    showMenu: Boolean,
    profiles: List<UserProfile>,
    selectedUserId: Int,
    onSelect: (UserProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 200.dp, max = 320.dp)
    ) {
        profiles.forEach { profile ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(profile.displayName())
                        Text(
                            stringResource(R.string.user_id, profile.id),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (profile.blocksShell) {
                            Text(
                                stringResource(R.string.profile_blocks_shell),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (profile.blocksUninstall) {
                            Text(
                                stringResource(R.string.profile_blocks_uninstall),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                leadingIcon = { Icon(profile.icon(), contentDescription = null) },
                trailingIcon = {
                    if (profile.id == selectedUserId) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
                onClick = {
                    onSelect(profile)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
fun UserProfile.displayName(): String {
    return when (kind) {
        UserProfile.Kind.PERSONAL -> stringResource(R.string.profile_personal)
        UserProfile.Kind.WORK -> stringResource(R.string.profile_work)
        UserProfile.Kind.CLONE -> stringResource(R.string.profile_clone)
        UserProfile.Kind.PRIVATE -> stringResource(R.string.profile_private)
        UserProfile.Kind.USER -> name ?: stringResource(R.string.profile_user)
        UserProfile.Kind.OTHER -> name ?: stringResource(R.string.profile_other)
    }
}

fun UserProfile.icon(): ImageVector {
    return when (kind) {
        UserProfile.Kind.PERSONAL -> Icons.Default.Person
        UserProfile.Kind.WORK -> Icons.Default.Work
        UserProfile.Kind.CLONE -> Icons.Default.ContentCopy
        UserProfile.Kind.PRIVATE -> Icons.Default.Lock
        UserProfile.Kind.USER -> Icons.Default.AccountCircle
        UserProfile.Kind.OTHER -> Icons.Default.SupervisedUserCircle
    }
}
