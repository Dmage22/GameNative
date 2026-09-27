package app.gamenative.wrapper

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Credits that apply to every wrapper build, whatever game the preset is for. */
private val ENGINE_CREDITS = listOf(
    WrapperCredit("GameNative", "The Android app this wrapper is built from, by Utkarsh Dalal and contributors (GPL-3.0). github.com/utkarshdalal/GameNative"),
    WrapperCredit("Winlator", "The container, X server and input foundation GameNative grew from, by brunodev85 and contributors."),
    WrapperCredit("Wine and Proton", "Windows compatibility layer by the Wine project, CodeWeavers and Valve (LGPL-2.1)."),
    WrapperCredit("proton-wine builds", "ARM64EC Proton packaging and optimizations from The412Banner/proton-wine and GameNative's ntsync-android."),
    WrapperCredit("FEX-Emu", "x86 emulation on ARM64 by the FEX-Emu team (MIT)."),
    WrapperCredit("DXVK", "Direct3D 8-11 to Vulkan by Philip Rebohle (zlib), gplasync patch by Ph42oN."),
    WrapperCredit("Mesa / Turnip", "Open-source Vulkan driver for Adreno GPUs by the Mesa project (MIT)."),
    WrapperCredit("llvm-mingw", "Windows ARM64EC toolchain by Martin Storsjö, with work by bylaws."),
)

@Composable
fun WrapperCreditsDialog(preset: WrapperPreset?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Credits") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val gameCredits = preset?.credits.orEmpty()
                if (gameCredits.isNotEmpty()) {
                    Section(preset?.name.orEmpty(), gameCredits)
                }
                Section("Built with", ENGINE_CREDITS)
                Text(
                    text = "Unofficial community build. Not affiliated with or endorsed by the projects listed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
    )
}

@Composable
private fun Section(title: String, credits: List<WrapperCredit>) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    credits.forEach { credit ->
        Text(text = credit.name, style = MaterialTheme.typography.bodyMedium)
        if (credit.detail.isNotEmpty()) {
            Text(
                text = credit.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
