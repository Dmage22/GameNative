package app.gamenative.wrapper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.ui.util.SnackbarManager
import java.io.File
import timber.log.Timber

/** Plain-text editor for the game's own config file (e.g. legends.ini). */
@Composable
fun ConfigFileEditorDialog(file: File, onDismiss: () -> Unit) {
    val exists = remember(file) { file.isFile }
    var text by remember(file) { mutableStateOf(if (exists) file.readText() else "") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = file.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (exists) file.path else "${file.name} not found yet - it appears after the game is installed and run once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 12.dp),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = file.parentFile?.isDirectory == true,
                        onClick = {
                            try {
                                file.writeText(text)
                                SnackbarManager.show("Saved ${file.name}")
                                onDismiss()
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to save ${file.path}")
                                SnackbarManager.show("Could not save ${file.name}")
                            }
                        },
                    ) { Text("Save") }
                }
            }
        }
    }
}
