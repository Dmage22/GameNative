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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun WrapperGpuDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var fpsLimitEnabled by remember { mutableStateOf(WrapperSettings.fpsLimitEnabled(context)) }
    var fpsLimitTarget by remember { mutableFloatStateOf(WrapperSettings.fpsLimitTarget(context).toFloat()) }
    var gpuDriver by remember { mutableStateOf(WrapperSettings.gpuDriver(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GPU") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Frame rate limit", modifier = Modifier.weight(1f))
                    Switch(
                        checked = fpsLimitEnabled,
                        onCheckedChange = {
                            fpsLimitEnabled = it
                            WrapperSettings.setFpsLimitEnabled(context, it)
                        },
                    )
                }
                if (fpsLimitEnabled) {
                    Slider(
                        value = fpsLimitTarget,
                        onValueChange = { fpsLimitTarget = it },
                        onValueChangeFinished = {
                            WrapperSettings.setFpsLimitTarget(context, fpsLimitTarget.toInt())
                        },
                        valueRange = 30f..144f,
                        steps = 113,
                    )
                    Text(text = "${fpsLimitTarget.toInt()} fps", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = "Graphics driver",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                WrapperGpuDriverOption(
                    title = "Turnip T30 (bundled)",
                    subtitle = "Recommended for Snapdragon (Adreno 6xx/7xx/8xx)",
                    selected = gpuDriver == WrapperSettings.DRIVER_BUNDLED,
                    onSelect = {
                        gpuDriver = WrapperSettings.DRIVER_BUNDLED
                        WrapperSettings.setGpuDriver(context, WrapperSettings.DRIVER_BUNDLED)
                    },
                )
                WrapperGpuDriverOption(
                    title = "System driver",
                    subtitle = "Use if the bundled driver has problems",
                    selected = gpuDriver == WrapperSettings.DRIVER_SYSTEM,
                    onSelect = {
                        gpuDriver = WrapperSettings.DRIVER_SYSTEM
                        WrapperSettings.setGpuDriver(context, WrapperSettings.DRIVER_SYSTEM)
                    },
                )
            }
        },
    )
}

@Composable
private fun WrapperGpuDriverOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}