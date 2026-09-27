package app.gamenative.wrapper

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The wrapper's own boot screen: the preset's splash image, the current boot step and an abort button.
 * Replaces GameNative's splash (hero backdrop, tips, sponsor/recommendation cards).
 */
@Composable
fun WrapperBootSplash(
    visible: Boolean,
    text: String,
    onAbort: () -> Unit,
) {
    if (!visible) return
    val context = LocalContext.current
    val image = remember {
        val preset = WrapperPresetLoader.load(context)
        val name = preset?.splash?.ifEmpty { preset.background }.orEmpty()
        if (name.isEmpty()) {
            null
        } else {
            runCatching {
                context.assets.open("wrapper/$name").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(bottom = 96.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.White)
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        TextButton(
            onClick = onAbort,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Text("Cancel", color = Color.White.copy(alpha = 0.8f))
        }
    }
}
