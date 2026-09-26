package app.gamenative.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.screen.controls.ControlsEditorActivity
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.util.SnackbarManager
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.InputControlsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsGroupControls() {
    val context = LocalContext.current

    // Load the on-screen controls profiles off the main thread
    var profiles by remember { mutableStateOf<List<ControlsProfile>>(emptyList()) }
    LaunchedEffect(Unit) {
        profiles = withContext(Dispatchers.IO) {
            InputControlsManager(context).getProfiles(true)
        }
    }

    SettingsGroup(
        modifier = Modifier.background(Color.Transparent),
        title = { Text(text = stringResource(R.string.settings_controls_title)) },
    ) {
        profiles.forEach { profile ->
            SettingsMenuLink(
                colors = settingsTileColors(),
                title = { Text(text = profile.name) },
                onClick = {
                    context.startActivity(ControlsEditorActivity.intent(context, profile.id))
                },
            )
        }

        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.settings_controls_import_profile)) },
            onClick = {
                SnackbarManager.show(context.getString(R.string.settings_controls_coming_soon))
            },
        )

        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.settings_controls_export_all_profiles)) },
            onClick = {
                SnackbarManager.show(context.getString(R.string.settings_controls_coming_soon))
            },
        )
    }
}