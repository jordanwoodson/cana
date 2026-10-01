package io.github.samolego.canta.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.riskColors
import io.github.samolego.canta.ui.riskIcon
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.theme.LocalCanaColors
import io.github.samolego.canta.ui.theme.RiskColors
import io.github.samolego.canta.util.RemovalRecommendation

enum class StatusTone { NEUTRAL, WARNING, ERROR }

@Composable
fun RiskChip(rec: RemovalRecommendation, modifier: Modifier = Modifier) {
    val label = stringResource(when (rec) {
        RemovalRecommendation.RECOMMENDED -> R.string.risk_recommended
        RemovalRecommendation.ADVANCED -> R.string.risk_advanced
        RemovalRecommendation.EXPERT -> R.string.risk_expert
        RemovalRecommendation.UNSAFE -> R.string.risk_unsafe
        RemovalRecommendation.SYSTEM -> R.string.risk_system
    })
    CompactChip(label, rec.riskIcon(), rec.riskColors(), modifier)
}

@Composable
fun StatusChip(label: String, icon: ImageVector, tone: StatusTone = StatusTone.NEUTRAL, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = when (tone) {
        StatusTone.NEUTRAL -> RiskColors(scheme.surfaceVariant, scheme.onSurfaceVariant)
        StatusTone.WARNING -> LocalCanaColors.current.advanced
        StatusTone.ERROR -> RiskColors(scheme.errorContainer, scheme.onErrorContainer)
    }
    CompactChip(label, icon, colors, modifier)
}

@Composable
private fun CompactChip(label: String, icon: ImageVector, colors: RiskColors, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {}
            .padding(2.dp).height(24.dp)
            .background(colors.container, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onContainer, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onContainer, maxLines = 1)
    }
}

@Composable
fun CanaBanner(
    icon: ImageVector,
    text: String,
    tone: StatusTone = StatusTone.NEUTRAL,
    modifier: Modifier = Modifier,
    details: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        StatusTone.NEUTRAL -> scheme.secondaryContainer to scheme.onSecondaryContainer
        StatusTone.WARNING -> LocalCanaColors.current.advanced.let { it.container to it.onContainer }
        StatusTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = content)
                actions()
            }
            details?.let { Column(Modifier.fillMaxWidth().padding(start = 42.dp, end = 12.dp, bottom = 8.dp)) { it() } }
        }
    }
}

@Composable
fun InfoDisclosure(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.maintenance_collapse else R.string.maintenance_expand),
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(expanded) { Column(Modifier.padding(bottom = 8.dp)) { content() } }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

data class ChoiceOption<T>(val value: T, val label: String, val testTag: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceRow(
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option.value,
                onClick = { onSelect(option.value) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(option.testTag),
                label = { Text(option.label, maxLines = 1) },
            )
        }
    }
}

@Preview(name = "Shared components light", showBackground = true)
@Composable
private fun ComponentsLightPreview() = ComponentsPreview(dark = false)

@Preview(name = "Shared components dark", showBackground = true)
@Composable
private fun ComponentsDarkPreview() = ComponentsPreview(dark = true)

@Composable
private fun ComponentsPreview(dark: Boolean) {
    CantaTheme(darkTheme = dark) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RiskChip(RemovalRecommendation.RECOMMENDED)
            StatusChip("Needs review", Icons.Default.Warning, StatusTone.WARNING)
            CanaBanner(Icons.Default.Info, "One update remains", StatusTone.NEUTRAL) {
                TextButton(onClick = {}) { Text("Review") }
            }
            SectionHeader("Overview")
            InfoDisclosure("About this size") { Text("Includes APK files only.") }
            EmptyState(Icons.Default.Info, "No apps", "Try another profile.")
            ChoiceRow(listOf(ChoiceOption(0, "Clipboard", "preview-clipboard"), ChoiceOption(1, "Text", "preview-text")), 0, {})
        }
    }
}
