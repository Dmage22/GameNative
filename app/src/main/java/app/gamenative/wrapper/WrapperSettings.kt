package app.gamenative.wrapper

import android.content.Context

/** Small persisted settings for the wrapper home screen dialogs (GPU / Display). */
object WrapperSettings {
    private const val FILE = "wrapper_settings"

    private const val FPS_LIMIT_ENABLED = "fps_limit_enabled"
    private const val FPS_LIMIT_TARGET = "fps_limit_target"
    private const val GPU_DRIVER = "gpu_driver"
    private const val SCREEN_SIZE = "screen_size"

    const val DRIVER_BUNDLED = "bundled"
    const val DRIVER_SYSTEM = "system"

    val SCREEN_SIZES = listOf("800x600", "1024x768", "1280x720", "1280x1024", "1366x768", "1600x900", "1920x1080")

    /** Needs a Wine update; shown but disabled. */

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun fpsLimitEnabled(context: Context): Boolean = prefs(context).getBoolean(FPS_LIMIT_ENABLED, false)

    fun setFpsLimitEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(FPS_LIMIT_ENABLED, value).apply()
    }

    fun fpsLimitTarget(context: Context): Int = prefs(context).getInt(FPS_LIMIT_TARGET, 60)

    fun setFpsLimitTarget(context: Context, value: Int) {
        prefs(context).edit().putInt(FPS_LIMIT_TARGET, value).apply()
    }

    fun gpuDriver(context: Context): String = prefs(context).getString(GPU_DRIVER, DRIVER_BUNDLED) ?: DRIVER_BUNDLED

    fun setGpuDriver(context: Context, value: String) {
        prefs(context).edit().putString(GPU_DRIVER, value).apply()
    }

    fun screenSize(context: Context, default: String): String =
        prefs(context).getString(SCREEN_SIZE, null) ?: default

    fun setScreenSize(context: Context, value: String) {
        prefs(context).edit().putString(SCREEN_SIZE, value).apply()
    }
}