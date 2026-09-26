package app.gamenative.wrapper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

        WrapperButton(text = "Start game") {
            SnackbarManager.show("Coming soon")
        }
        Spacer(modifier = Modifier.height(16.dp))

        val openControls = {
            val profile = InputControlsManager(context).getProfiles(true).firstOrNull()
            if (profile == null) {
                SnackbarManager.show("No controls profile")
            } else {
                context.startActivity(ControlsEditorActivity.intent(context, profile.id))
            }
        }
        val smallButtons = buildList<Pair<String, () -> Unit>> {
            add("Setup" to { SnackbarManager.show("Coming soon") })
            add("Controls" to openControls)
            add("GPU" to { SnackbarManager.show("Coming soon") })
            add("Resolution" to { SnackbarManager.show("Coming soon") })
            if (preset.configFile.isNotEmpty()) {
                add(preset.configFile to { editingConfig = File(gameDir, preset.configFile) })
            }
        }
        // Two buttons per row; a lone last button keeps the same width as the others.
        smallButtons.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { (label, onClick) ->
                    WrapperSmallButton(text = label, onClick = onClick, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
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
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(72.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun WrapperSmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .padding(vertical = 6.dp)
            .height(44.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
