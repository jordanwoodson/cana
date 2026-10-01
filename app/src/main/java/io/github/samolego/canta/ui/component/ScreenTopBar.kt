package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.theme.CantaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
        onNavigateBack: () -> Unit,
        title: @Composable () -> Unit,
        subtitle: String? = null,
        containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
        actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
            colors =
                    TopAppBarDefaults.topAppBarColors(
                            containerColor = containerColor
                    ),
            title = {
                if (subtitle == null) title()
                else androidx.compose.foundation.layout.Column {
                    title()
                    androidx.compose.material3.Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            },
            navigationIcon = {
                IconClickButton(
                        onClick = onNavigateBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                )
            },
            actions = actions,
    )
}

@Preview(name = "Screen top bar light", showBackground = true)
@Composable
private fun ScreenTopBarLightPreview() {
    CantaTheme(darkTheme = false) { ScreenTopBar({}, { androidx.compose.material3.Text("App details") }, subtitle = "Work profile") }
}

@Preview(name = "Screen top bar dark", showBackground = true)
@Composable
private fun ScreenTopBarDarkPreview() {
    CantaTheme(darkTheme = true) { ScreenTopBar({}, { androidx.compose.material3.Text("App details") }, subtitle = "Work profile") }
}
