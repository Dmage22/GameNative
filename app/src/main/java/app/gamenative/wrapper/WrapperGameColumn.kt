package app.gamenative.wrapper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.ui.component.QuickMenuAction
import kotlinx.coroutines.delay

/**
 * The wrapper's in-game column, replacing GameNative's Quick Menu (see docs/wrapper.md).
 * Left edge; client tabs in the top half, tools in the bottom half. Collapses to a thin handle.
 *
 * [menuRequested] is GameNative's "open the Quick Menu" signal (back key, edge handle, ...): it toggles
 * the column, then [onMenuRequestHandled] resets it. Tools run through GameNative's own Quick Menu
 * actions via [onAction].
 */
@Composable
fun WrapperGameColumn(
    menuRequested: Boolean,
    onMenuRequestHandled: () -> Unit,
    onAction: (Int) -> Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    // The handle pulses for a few seconds after the game starts so new players notice it.
    var pulsing by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(6000)
        pulsing = false
    }
    val pulse = rememberInfiniteTransition(label = "handle")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "handleAlpha",
    )
    val handleAlpha = if (pulsing) pulseAlpha else 0.35f

    LaunchedEffect(menuRequested) {
        if (menuRequested) {
            expanded = !expanded
            onMenuRequestHandled()
        }
    }

    fun run(action: Int) {
        expanded = false
        onAction(action)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!expanded) {
            // Thin handle on the left edge.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(14.dp)
                    .height(72.dp)
                    .clickable { expanded = true },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(56.dp)
                        .background(Color.White.copy(alpha = handleAlpha), RoundedCornerShape(3.dp)),
                )
            }
        }

        if (expanded) {
            // Tapping anywhere outside the column closes it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { expanded = false } },
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(60.dp)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top half: client tabs (multi-client comes later; one client for now).
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ClientTab(number = 1, selected = true) { expanded = false }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 10.dp))

                // Bottom half: tools.
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                ) {
                    Tool(Icons.Default.Edit, "Edit controls") { run(QuickMenuAction.EDIT_CONTROLS) }
                    Tool(Icons.Default.Visibility, "Show or hide controls") { run(QuickMenuAction.INPUT_CONTROLS) }
                    Tool(Icons.Default.Keyboard, "Keyboard") { run(QuickMenuAction.KEYBOARD) }
                    Tool(Icons.Default.QueryStats, "Performance overlay") { run(QuickMenuAction.PERFORMANCE_HUD) }
                    Tool(Icons.AutoMirrored.Filled.ExitToApp, "Exit game") { confirmExit = true }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Exit the game?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    run(QuickMenuAction.EXIT_GAME)
                }) { Text("Exit") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ClientTab(number: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = number.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun Tool(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}
