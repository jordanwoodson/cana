package io.github.samolego.canta.ui.menu

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.navigation.Screen

@Composable
fun MoreOptionsMenu(
        showMenu: Boolean,
        navigateToPage: (route: String) -> Unit,
        onScanTrackers: () -> Unit,
        onExportCsv: () -> Unit,
        onDismiss: () -> Unit,
) {

    DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismiss,
            modifier = Modifier.widthIn(min = 200.dp)
    ) {
        DropdownMenuItem(text = { Text(stringResource(R.string.presets)) },
            leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
            onClick = { navigateToPage(Screen.Presets.route); onDismiss() })
        DropdownMenuItem(text = { Text(stringResource(R.string.history)) },
            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
            onClick = { navigateToPage(Screen.History.route); onDismiss() })
        HorizontalDivider()
        DropdownMenuItem(text = { Text(stringResource(R.string.privacy_dashboard)) },
            leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
            onClick = { navigateToPage(Screen.Privacy.route); onDismiss() })
        DropdownMenuItem(text = { Text(stringResource(R.string.comparison_title)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null) },
            onClick = { navigateToPage(Screen.Comparison.route); onDismiss() })
        DropdownMenuItem(text = { Text(stringResource(R.string.device_settings)) },
            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
            onClick = { navigateToPage(Screen.System.route); onDismiss() })
        HorizontalDivider()
        DropdownMenuItem(text = { Text(stringResource(R.string.tracker_scan_all)) },
            leadingIcon = { Icon(Icons.Default.TrackChanges, contentDescription = null) },
            onClick = { onScanTrackers(); onDismiss() })
        DropdownMenuItem(text = { Text(stringResource(R.string.package_csv_export)) },
            leadingIcon = { Icon(Icons.Default.TableView, contentDescription = null) },
            onClick = { onExportCsv(); onDismiss() })
        HorizontalDivider()
        DropdownMenuItem(text = { Text(stringResource(R.string.settings)) },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = { navigateToPage(Screen.Settings.route); onDismiss() })
    }
}
