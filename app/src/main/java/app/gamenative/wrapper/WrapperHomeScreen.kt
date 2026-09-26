package app.gamenative.wrapper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.gamenative.ui.screen.controls.ControlsEditorActivity
import app.gamenative.ui.util.SnackbarManager
import com.winlator.inputcontrols.InputControlsManager

@Composable
fun WrapperHomeScreen(
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val preset = remember { WrapperPresetLoader.load(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        Text(
            text = preset.name,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Game not installed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))

        WrapperButton(text = "Import game…") {
            SnackbarManager.show("Coming soon")
        }
        Spacer(modifier = Modifier.height(16.dp))
        WrapperButton(text = "Play") {
            SnackbarManager.show("Coming soon")
        }
        Spacer(modifier = Modifier.height(16.dp))
        WrapperButton(text = "Controls") {
            val profiles = InputControlsManager(context).getProfiles(true)
            val profile = profiles.firstOrNull()
            if (profile == null) {
                SnackbarManager.show("No controls profile")
            } else {
                context.startActivity(ControlsEditorActivity.intent(context, profile.id))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        WrapperButton(text = "Settings") {
            onOpenSettings()
        }
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
            .height(56.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}