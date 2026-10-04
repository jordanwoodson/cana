package io.github.samolego.canta.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsItem(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    isSwitch: Boolean = false,
    checked: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val action = if (isSwitch) Modifier.toggleable(
        value = checked,
        enabled = enabled && onCheckedChange != null,
        role = Role.Switch,
        onValueChange = { value ->
            onClick?.invoke()
            onCheckedChange?.invoke(value)
        },
    ) else if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
    else Modifier
    val showIcon = icon != null && LocalDensity.current.fontScale < 1.3f
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).then(action).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showIcon && icon != null) {
                Surface(Modifier.padding(end = 12.dp).size(36.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
                            tint = if (enabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    }
                }
            }
            Text(title, Modifier.weight(1f).padding(end = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            if (isSwitch) Switch(checked = checked, enabled = enabled, onCheckedChange = null)
            else if (onClick != null) Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        description?.let {
            Text(it, Modifier.padding(top = 8.dp, start = if (showIcon) 48.dp else 0.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        }
    }
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(content = content)
    }
}

@Composable
fun SettingsGroupDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SettingsTextItem(
        title: String,
        description: String? = null,
        icon: ImageVector? = null,
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String? = null,
        keyboardType: KeyboardType = KeyboardType.Text,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
        if (icon != null) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp, top = 12.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
            )
            if (description != null) {
                Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = placeholder?.let { { Text(it, softWrap = false) } },
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    singleLine = true,
            )
        }
    }
}
