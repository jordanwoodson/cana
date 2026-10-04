package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.theme.CantaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
        onNavigateBack: () -> Unit,
        title: @Composable () -> Unit,
        subtitle: String? = null,
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
        actions: @Composable RowScope.() -> Unit = {},
) {
    // Let translated titles and enlarged text set the height instead of clipping
    // them into the fixed title slot of a small app bar.
    Surface(color = containerColor) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .heightIn(min = 64.dp).padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconClickButton(
                onClick = onNavigateBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                androidx.compose.foundation.layout.Box(Modifier.semantics(mergeDescendants = true) { heading() }) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                }
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Preview(name = "Screen top bar Dracula", showBackground = true, backgroundColor = 0xFF282A36)
@Composable
private fun ScreenTopBarPreview() {
    CantaTheme { ScreenTopBar({}, { androidx.compose.material3.Text("App details") }, subtitle = "Work profile") }
}
