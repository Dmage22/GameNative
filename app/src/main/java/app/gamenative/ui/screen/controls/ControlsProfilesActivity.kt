package app.gamenative.ui.screen.controls

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.theme.PluviaTheme
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.InputControlsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Full-screen manager for on-screen controls profiles: list, create, import,
 * rename, duplicate, export and delete.
 */
class ControlsProfilesActivity : ComponentActivity() {

    // Incremented to reload the profile list after every change and on resume.
    private val refresh = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = InputControlsManager(this)
        setContent {
            PluviaTheme {
                ControlsProfilesScreen(
                    manager = manager,
                    refresh = refresh.intValue,
                    onRefresh = { refresh.intValue++ },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh when coming back (e.g. from the editor) so the list stays current.
        refresh.intValue++
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, ControlsProfilesActivity::class.java)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlsProfilesScreen(
    manager: InputControlsManager,
    refresh: Int,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // SnackbarManager is only displayed by the main screen, so this activity shows its own messages.
    val snackbarHostState = remember { SnackbarHostState() }
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    var profiles by remember { mutableStateOf<List<ControlsProfile>>(emptyList()) }
    LaunchedEffect(refresh) {
        // Reload from disk: other screens (e.g. the editor's Duplicate) use their own manager instance.
        profiles = withContext(Dispatchers.IO) {
            manager.loadProfiles(true)
            manager.getProfiles(true).toList()
        }
    }

    var showNewDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ControlsProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<ControlsProfile?>(null) }
    var exportTarget by remember { mutableStateOf<ControlsProfile?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val profile = exportTarget
        exportTarget = null
        if (uri != null && profile != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val bytes = ControlsProfile.getProfileFile(context, profile.id).readBytes()
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    }
                    showMessage(context.getString(R.string.controls_profiles_exported, profile.name))
                } catch (e: Exception) {
                    showMessage(context.getString(R.string.controls_profiles_export_failed))
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    try {
                        val text = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.use { it.readText() }
                        text?.let { manager.importProfile(JSONObject(it)) }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (imported != null) {
                    showMessage(context.getString(R.string.controls_profiles_imported, imported.name))
                } else {
                    showMessage(context.getString(R.string.controls_profiles_import_failed))
                }
                onRefresh()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.controls_profiles_title)) },
                navigationIcon = {
                    IconButton(onClick = { (context as? ControlsProfilesActivity)?.finish() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showNewDialog = true }) {
                        Text(text = stringResource(R.string.controls_profiles_new))
                    }
                    TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Text(text = stringResource(R.string.controls_profiles_import))
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.controls_profiles_empty),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(profiles, key = { it.id }) { profile ->
                        var menuExpanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(ControlsEditorActivity.intent(context, profile.id))
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = profile.name,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.controls_profiles_edit)) },
                                    onClick = {
                                        menuExpanded = false
                                        context.startActivity(ControlsEditorActivity.intent(context, profile.id))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.controls_profiles_rename)) },
                                    onClick = {
                                        menuExpanded = false
                                        renameTarget = profile
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.controls_profiles_duplicate)) },
                                    onClick = {
                                        menuExpanded = false
                                        scope.launch {
                                            withContext(Dispatchers.IO) { manager.duplicateProfile(profile) }
                                            onRefresh()
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.controls_profiles_export)) },
                                    onClick = {
                                        menuExpanded = false
                                        exportTarget = profile
                                        exportLauncher.launch("${profile.name}.icp")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.controls_profiles_delete)) },
                                    onClick = {
                                        menuExpanded = false
                                        deleteTarget = profile
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        NameDialog(
            title = stringResource(R.string.controls_profiles_new),
            initialName = stringResource(R.string.controls_profiles_new_profile_name),
            onDismiss = { showNewDialog = false },
            onConfirm = { name ->
                showNewDialog = false
                scope.launch {
                    withContext(Dispatchers.IO) { manager.createProfile(name) }
                    onRefresh()
                }
            },
        )
    }

    renameTarget?.let { profile ->
        NameDialog(
            title = stringResource(R.string.controls_profiles_rename),
            initialName = profile.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        profile.setName(name)
                        profile.save()
                    }
                    onRefresh()
                }
            },
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = stringResource(R.string.controls_profiles_delete)) },
            text = { Text(text = stringResource(R.string.controls_profiles_delete_confirm, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        withContext(Dispatchers.IO) { manager.removeProfile(profile) }
                        onRefresh()
                    }
                }) {
                    Text(text = stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(text = stringResource(R.string.controls_profiles_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}