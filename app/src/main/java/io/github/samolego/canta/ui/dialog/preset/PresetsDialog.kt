package io.github.samolego.canta.ui.dialog.preset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.component.ChoiceOption
import io.github.samolego.canta.ui.component.ChoiceRow

@Composable
fun ImportPresetDialog(
    onDismiss: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onImportFromText: (String) -> Unit,
    importError: String? = null,
    onTextChanged: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(Tab.CLIPBOARD) }
    var jsonText by rememberSaveable { mutableStateOf("") }
    var textError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Own the keyboard/system insets instead of allowing the floating window
        // to pan a tall, enlarged-text dialog partly offscreen.
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier.safeDrawingPadding(),
        title = {
            Text(
                text = stringResource(R.string.import_preset),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.import_preset_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ChoiceRow(
                    options = listOf(
                        ChoiceOption(Tab.CLIPBOARD, stringResource(R.string.clipboard), "preset-import-clipboard"),
                        ChoiceOption(Tab.TEXT, stringResource(R.string.text), "preset-import-text"),
                    ),
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                )

                importError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    Tab.CLIPBOARD -> {
                        Text(
                            text = stringResource(R.string.import_preset_clipboard_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Tab.TEXT -> {
                        // Text import
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = jsonText,
                                onValueChange = {
                                    jsonText = it
                                    textError = false
                                    onTextChanged()
                                },
                                label = { Text(stringResource(R.string.paste_preset_json)) },
                                placeholder = { Text(stringResource(R.string.paste_preset_json_here)) },
                                isError = textError,
                                supportingText = if (textError) {
                                    { Text(stringResource(R.string.enter_valid_json)) }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 240.dp),
                                minLines = 4,
                                maxLines = 8,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (selectedTab) {
                Tab.CLIPBOARD -> {
                    Button(onClick = onImportFromClipboard) {
                        Text(stringResource(R.string.preset_review_button))
                    }
                }
                Tab.TEXT -> {
                    Button(
                        onClick = {
                            if (jsonText.isNotBlank()) {
                                onImportFromText(jsonText.trim())
                            } else {
                                textError = true
                            }
                        },
                        enabled = jsonText.isNotBlank()
                    ) {
                        Text(stringResource(R.string.preset_review_button))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

enum class Tab {
    CLIPBOARD,
    TEXT,
}
