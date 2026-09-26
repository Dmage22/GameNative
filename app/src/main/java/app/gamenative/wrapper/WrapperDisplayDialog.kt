package app.gamenative.wrapper

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun WrapperDisplayDialog(defaultSize: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(WrapperSettings.screenSize(context, defaultSize)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Display") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                WrapperSettings.SCREEN_SIZES.forEach { size ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == size,
                            onClick = {
                                selected = size
                                WrapperSettings.setScreenSize(context, size)
                            },
                        )
                        Text(text = size.replace("x", " x "), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    text = "The game only sees resolutions that fit inside this size. To use 1366 x 768 in the game, pick 1366 x 768 or larger here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
    )
}