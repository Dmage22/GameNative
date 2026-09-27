package app.gamenative.wrapper

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import app.gamenative.ui.components.rememberCustomGameFolderPicker
import app.gamenative.ui.components.requestPermissionsForPath
import app.gamenative.utils.CustomGameScanner
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
    var installedPkgUri by remember { mutableStateOf<Uri?>(null) }

    var showSetup by remember { mutableStateOf(false) }
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
                installedPkgUri = uri
                SnackbarManager.show("${p.name} installed")
            }.onFailure {
                Timber.e(it, "Wrapper: .pkg install failed")
                SnackbarManager.show(it.message ?: "Install failed")
            }
        }
    }

    val folderPicker = rememberCustomGameFolderPicker(
        onPathSelected = { path ->
            val p = preset
            if (p == null) {
                SnackbarManager.show("Preset missing")
            } else if (!CustomGameScanner.hasStoragePermission(context, path)) {
                requestPermissionsForPath(context, path, null)
                SnackbarManager.show("Allow file access, then tap Setup again")
            } else scope.launch {
                busyMessage = "Setting up…"
                val result = withContext(Dispatchers.IO) {
                    runCatching { WrapperSetup.registerGameFolder(context, p, path) }
                }
                busyMessage = null
                result.onSuccess {
                    installedVersion++
                    SnackbarManager.show("${p.name} found")
                }.onFailure { SnackbarManager.show(it.message ?: "Setup failed") }
            }
        },
        onFailure = { SnackbarManager.show(it) },
    )

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
                text = busyMessage ?: if (installed) "Ready" else "Game not installed - tap Setup",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                enabled = busyMessage == null,
                onClick = {
                    if (!installed) {
                        SnackbarManager.show("Run Setup first")
                    } else scope.launch {
                        busyMessage = "Preparing…"
                        val result = withContext(Dispatchers.IO) {
                            runCatching { WrapperSetup.prepare(context, preset) { msg -> busyMessage = msg } }
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
        val smallButtons = buildList<Pair<String, () -> Unit>> {
            add("Setup" to { showSetup = true })
            add("Controls" to openControls)
            add("GPU" to { showGpu = true })
            add("Display" to { showDisplay = true })
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
            title = { Text("Setup") },
            text = { Text("Install the game from the Mac .pkg you downloaded, or use a folder where it is already extracted.") },
            confirmButton = {
                TextButton(onClick = {
                    showSetup = false
                    pkgPicker.launch(arrayOf("*/*"))
                }) { Text("Install from .pkg") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSetup = false
                    folderPicker.launchPicker()
                }) { Text("Use extracted folder") }
            },
        )
    }

    if (showGpu) {
        WrapperGpuDialog(onDismiss = { showGpu = false })
    }
    if (showDisplay) {
        WrapperDisplayDialog(
            defaultSize = preset?.screenSize.orEmpty().ifEmpty { "1280x1024" },
            onDismiss = { showDisplay = false },
        )
    }

    installedPkgUri?.let { pkgUri ->
        val (pkgName, pkgSizeBytes) = remember(pkgUri) { queryPkgInfo(context, pkgUri) }
        AlertDialog(
            onDismissRequest = { installedPkgUri = null },
            title = { Text("Delete the downloaded package?") },
            text = {
                val sizeText = pkgSizeBytes?.let { " to free about ${"%.1f".format(it / 1e9)} GB." } ?: "."
                Text("The game is installed. You can delete ${pkgName ?: "the package"}$sizeText")
            },
            confirmButton = {
                TextButton(onClick = {
                    installedPkgUri = null
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            runCatching {
                                DocumentsContract.deleteDocument(context.contentResolver, pkgUri)
                            }.getOrDefault(false)
                        }
                        SnackbarManager.show(if (deleted) "Package deleted" else "Could not delete - delete it in your file manager")
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { installedPkgUri = null }) { Text("Keep") }
            },
        )
    }
}

/** Looks up the display name and size of the picked package; either may be null. */
private fun queryPkgInfo(context: Context, uri: Uri): Pair<String?, Long?> = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val name = if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        val size = if (cursor.moveToFirst() && !cursor.isNull(1)) cursor.getLong(1) else null
        name to size
    } ?: (null to null)
}.getOrDefault(null to null)

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
