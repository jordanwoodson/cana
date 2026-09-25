package io.github.samolego.canta.ui.component.fab

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PackageAction

@Composable
fun PackageActionsFab(model: AppListViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.padding(16.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
        ExtendedFloatingActionButton(onClick = { model.requestAction(model.defaultAction) }) {
            Text(stringResource(model.defaultAction.label))
        }
        Box {
            IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
            DropdownMenu(expanded, { expanded = false }) {
                listOf(PackageAction.DISABLE, PackageAction.ENABLE, PackageAction.SUSPEND, PackageAction.UNSUSPEND,
                    PackageAction.UNINSTALL_KEEP_DATA, PackageAction.UNINSTALL).forEach { action ->
                    DropdownMenuItem(text = { Text(stringResource(action.label)) }, onClick = {
                        expanded = false
                        model.requestAction(action)
                    })
                }
            }
        }
    }
}
