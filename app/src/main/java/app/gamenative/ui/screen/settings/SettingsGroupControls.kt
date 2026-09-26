package app.gamenative.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.screen.controls.ControlsProfilesActivity
import app.gamenative.ui.theme.settingsTileColors
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink

@Composable
fun SettingsGroupControls() {
    val context = LocalContext.current

    SettingsGroup(
        modifier = Modifier.background(Color.Transparent),
        title = { Text(text = stringResource(R.string.settings_controls_title)) },
    ) {
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.controls_profiles_manage)) },
            onClick = {
                context.startActivity(ControlsProfilesActivity.intent(context))
            },
        )
    }
}