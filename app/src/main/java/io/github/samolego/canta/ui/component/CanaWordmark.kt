package io.github.samolego.canta.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.theme.JetBrainsMono

@Composable
fun CanaWordmark(modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    val appName = stringResource(R.string.app_name)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary)) { append("(") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append("can") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary)) { append(")") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append("a") }
        },
        modifier = modifier.clearAndSetSemantics { contentDescription = appName },
        style = style,
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
    )
}

@Composable
fun CanaBrandLockup(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
            modifier = Modifier.size(88.dp))
        CanaWordmark(style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.brand_tagline), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
