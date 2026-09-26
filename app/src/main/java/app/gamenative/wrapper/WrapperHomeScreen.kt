package app.gamenative.wrapper

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import app.gamenative.ui.screen.controls.ControlsProfilesActivity
import app.gamenative.ui.util.SnackbarManager
import java.io.File

@Composable
fun WrapperHomeScreen() {
    val context = LocalContext.current
    val preset = remember { WrapperPresetLoader.load(context) }
    var editingConfig by remember { mutableStateOf<File?>(null) }

    val background = remember(preset) {
        preset?.background?.takeIf { it.isNotEmpty() }?.let { name ->
            runCatching {
                context.assets.open("wrapper/$name").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (background != null) {
            Image(
                bitmap = background,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = 0.35f,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (preset == null) {
            Text(
                text = "Preset missing",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        val gameDir = remember(preset) { WrapperPaths.gameDir(context, preset) }
        val installed = File(gameDir, preset.install.exe).isFile

        // Title and Start game hug the top, the other buttons hug the bottom, leaving the logo visible in between.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (installed) "Ready" else "Game not installed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { SnackbarManager.show("Coming soon") }) {
                Text(
                    text = "Start game",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        val openControls = {
            context.startActivity(ControlsProfilesActivity.intent(context))
        }
        val smallButtons = buildList<Pair<String, () -> Unit>> {
            add("Setup" to { SnackbarManager.show("Coming soon") })
            add("Controls" to openControls)
            add("GPU" to { SnackbarManager.show("Coming soon") })
            add("Display" to { SnackbarManager.show("Coming soon") })
            if (preset.configFile.isNotEmpty()) {
                add(preset.configFile to { editingConfig = File(gameDir, preset.configFile) })
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
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
    }

    editingConfig?.let { file ->
        ConfigFileEditorDialog(file = file, onDismiss = { editingConfig = null })
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
