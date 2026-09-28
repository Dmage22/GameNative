package app.gamenative.wrapper

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun WrapperHomeScreen(
    onStartGame: (appId: String) -> Unit,
) {
    val context = LocalContext.current
    val preset = remember { WrapperPresetLoader.load(context) }
    var editingConfig by remember { mutableStateOf<File?>(null) }
    var showGpu by remember { mutableStateOf(false) }
    var showDisplay by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var installedVersion by remember { mutableIntStateOf(0) }

    var showSetup by remember { mutableStateOf(false) }
    val folderImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val p = preset
        if (uri != null && p != null) scope.launch {
            busyMessage = "Copying… 0%"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    WrapperSetup.importFolder(context, p, uri) { progress ->
                        busyMessage = "Copying… ${(progress * 100).toInt()}%"
                    }
                }
            }
            busyMessage = null
            result.onSuccess {
                installedVersion++
                SnackbarManager.show("${p.name} imported")
            }.onFailure {
                Timber.e(it, "Wrapper: folder import failed")
                SnackbarManager.show(it.message ?: "Import failed")
            }
        }
    }
    var showCredits by remember { mutableStateOf(false) }
    val pkgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val p = preset
        if (uri != null && p != null) scope.launch {
            busyMessage = "Installing… 0%"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    WrapperSetup.installFromPkg(context, p, uri) { progress ->
                        busyMessage = "Installing… ${(progress * 100).toInt()}%"
                    }
                }
            }
            busyMessage = null
            result.onSuccess {
                installedVersion++
                SnackbarManager.show("${p.name} installed")
            }.onFailure {
                Timber.e(it, "Wrapper: .pkg install failed")
                SnackbarManager.show(it.message ?: "Install failed")
            }
        }
    }


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

        val gameDir = remember(preset, installedVersion) {
            WrapperSetup.gameFolder(context)?.let { File(it) } ?: WrapperPaths.gameDir(context, preset)
        }
        val installed = remember(preset, installedVersion) { WrapperSetup.isGameInstalled(context, preset) }
        val ready = remember(preset, installedVersion) { WrapperSetup.isEnvironmentReady(context) }
        val runSetup: () -> Unit = {
            if (!installed) {
                SnackbarManager.show("Choose the game files first")
            } else scope.launch {
                busyMessage = "Setting up…"
                val result = withContext(Dispatchers.IO) {
                    runCatching { WrapperSetup.setupEnvironment(context, preset) { msg -> busyMessage = msg } }
                }
                busyMessage = null
                installedVersion++
                result.onSuccess { SnackbarManager.show("Setup complete") }.onFailure {
                    Timber.e(it, "Wrapper: setup failed")
                    SnackbarManager.show(it.message ?: "Setup failed")
                }
            }
        }

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
                text = busyMessage ?: when {
                    !installed -> "Step 1: tap Game files"
                    !ready -> "Step 2: tap Setup"
                    else -> "Ready"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                enabled = busyMessage == null,
                onClick = {
                    if (!installed) {
                        SnackbarManager.show("Choose the game files first")
                    } else if (!ready) {
                        SnackbarManager.show("Tap Setup first")
                    } else scope.launch {
                        busyMessage = "Starting…"
                        val result = withContext(Dispatchers.IO) {
                            runCatching { WrapperSetup.prepareLaunch(context, preset) }
                        }
                        busyMessage = null
                        result.onSuccess(onStartGame).onFailure {
                            Timber.e(it, "Wrapper: prepare failed")
                            SnackbarManager.show(it.message ?: "Could not start the game")
                        }
                    }
                },
            ) {
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
        // Label, enabled, action. GPU/Display need Setup (the container); the config file needs the game files.
        val smallButtons = buildList<Triple<String, Boolean, () -> Unit>> {
            add(Triple("Game files", true) { showSetup = true })
            add(Triple("Setup", true, runSetup))
            add(Triple("Controls", true, openControls))
            add(Triple("GPU", ready) { showGpu = true })
            add(Triple("Display", ready) { showDisplay = true })
            if (preset.configFile.isNotEmpty()) {
                add(Triple(preset.configFile, installed) { editingConfig = File(gameDir, preset.configFile) })
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Two compact buttons per row, centred; they size to their label (with a small minimum width).
            smallButtons.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                ) {
                    row.forEach { (label, enabled, onClick) ->
                        WrapperSmallButton(
                            text = label,
                            enabled = enabled && busyMessage == null,
                            onClick = onClick,
                            modifier = Modifier.widthIn(min = 124.dp),
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = { showCredits = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
        ) {
            Text("Credits", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        }
    }

    if (showCredits) {
        WrapperCreditsDialog(preset = preset, onDismiss = { showCredits = false })
    }

    editingConfig?.let { file ->
        ConfigFileEditorDialog(file = file, onDismiss = { editingConfig = null })
    }

    if (showSetup) {
        AlertDialog(
            onDismissRequest = { showSetup = false },
            title = { Text("Game files") },
            text = {
                Text(
                    "Choose the ${preset?.name.orEmpty()} Mac .pkg you downloaded, or a folder where the game is " +
                        "already extracted. It is copied into this app; your file or folder is left untouched.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSetup = false
                    pkgPicker.launch(arrayOf("*/*"))
                }) { Text("Choose .pkg") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSetup = false
                    folderImporter.launch(null)
                }) { Text("Import folder") }
            },
        )
    }

    if (showGpu) {
        WrapperGpuDialog(onDismiss = { showGpu = false })
    }
    if (showDisplay) {
        WrapperDisplayDialog(
            defaultSize = preset?.screenSize.orEmpty().ifEmpty { "1366x768" },
            onDismiss = { showDisplay = false },
        )
    }
}

@Composable
private fun WrapperSmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .padding(vertical = 4.dp)
            .height(40.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
