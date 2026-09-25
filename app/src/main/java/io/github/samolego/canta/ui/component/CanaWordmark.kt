package io.github.samolego.canta.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
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

@Composable
fun CanaWordmark(modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    val appName = stringResource(R.string.app_name)
    Text(
        text = buildAnnotatedString {
            append("(")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("can") }
            append(")a")
        },
        modifier = modifier.clearAndSetSemantics { contentDescription = appName },
        style = style,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
    )
}
