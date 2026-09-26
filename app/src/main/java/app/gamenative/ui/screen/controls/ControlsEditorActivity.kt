package app.gamenative.ui.screen.controls

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import app.gamenative.ui.component.dialog.ElementEditorDialog
import app.gamenative.ui.screen.xserver.EditModeToolbar
import app.gamenative.ui.theme.PluviaTheme
import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.InputControlsManager
import com.winlator.widget.InputControlsView

/**
 * Full-screen, out-of-game editor for an on-screen controls profile.
 * Reuses the in-game edit-mode canvas ([InputControlsView]) and toolbar ([EditModeToolbar]).
 */
class ControlsEditorActivity : ComponentActivity() {

    private fun applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars(),
                )
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-apply immersive mode when window gains focus to ensure bars stay hidden
        if (hasFocus) applyImmersiveMode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveMode()

        val profileId = intent.getIntExtra(EXTRA_PROFILE_ID, -1)
        val profile = InputControlsManager(this).getProfile(profileId)
        if (profile == null) {
            Toast.makeText(this, "Profile not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            PluviaTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    val icView = remember { InputControlsView(this@ControlsEditorActivity) }
                    var elementToEdit by remember { mutableStateOf<ControlElement?>(null) }
                    var showElementEditor by remember { mutableStateOf(false) }

                    AndroidView(
                        factory = {
                            icView.apply {
                                setEditMode(true)
                                // Set the profile once the view has a size so elements
                                // are laid out against the real dimensions.
                                post { setProfile(profile) }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    EditModeToolbar(
                        onAdd = {
                            if (icView.addElement()) {
                                icView.invalidate()
                            }
                        },
                        onEdit = {
                            icView.getSelectedElement()?.let { element ->
                                elementToEdit = element
                                showElementEditor = true
                            }
                        },
                        onDelete = {
                            icView.removeElement()
                        },
                        onSave = {
                            profile.save()
                            icView.onControlsProfileContentChanged(false)
                            finish()
                        },
                        onClose = {
                            // Discard unsaved changes and close.
                            profile.loadElements(icView)
                            finish()
                        },
                        onDuplicate = { _ ->
                            // TODO: "copy from another profile" is not supported here yet.
                        },
                    )

                    if (showElementEditor && elementToEdit != null) {
                        ElementEditorDialog(
                            element = elementToEdit!!,
                            view = icView,
                            onDismiss = { showElementEditor = false },
                            onSave = { showElementEditor = false },
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PROFILE_ID = "profile_id"

        fun intent(context: Context, profileId: Int): Intent =
            Intent(context, ControlsEditorActivity::class.java).putExtra(EXTRA_PROFILE_ID, profileId)
    }
}