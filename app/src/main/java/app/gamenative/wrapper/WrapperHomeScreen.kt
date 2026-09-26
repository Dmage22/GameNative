package app.gamenative.wrapper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.gamenative.ui.screen.controls.ControlsEditorActivity
import app.gamenative.ui.util.SnackbarManager
import com.winlator.inputcontrols.InputControlsManager
import java.io.File

@Composable
fun WrapperHomeScreen() {
    val context = LocalContext.current
    val preset = remember { WrapperPresetLoader.load(context) }
    var editingConfig by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (preset == null) {
            Text(
                text = "Preset missing",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            return@Column
        }

        val gameDir = remember(preset) { WrapperPaths.gameDir(context, preset) }
        val installed = File(gameDir, preset.install.exe).isFile

        Text(
            text = preset.name,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (installed) "Ready" else "Game not installed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))

        WrapperButton(text = "Start game", primary = true) {
            SnackbarManager.show("Coming soon")
        }
        WrapperButton(text = "Import game…") {
            SnackbarManager.show("Coming soon")
        }
        WrapperButton(text = "GPU") {
            SnackbarManager.show("Coming soon")
        }
        WrapperButton(text = "Resolution") {
            SnackbarManager.show("Coming soon")
        }
        if (preset.configFile.isNotEmpty()) {
            WrapperButton(text = preset.configFile) {
                editingConfig = File(gameDir, preset.configFile)
            }
        }
        WrapperButton(text = "Controls") {
            val profile = InputControlsManager(context).getProfiles(true).firstOrNull()
            if (profile == null) {
                SnackbarManager.show("No controls profile")
            } else {
                context.startActivity(ControlsEditorActivity.intent(context, profile.id))
            }
        }
    }

    editingConfig?.let { file ->
        ConfigFileEditorDialog(file = file, onDismiss = { editingConfig = null })
    }
}

@Composable
private fun WrapperButton(
    text: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
        .height(56.dp)
    if (primary) {
        Button(onClick = onClick, modifier = modifier) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
