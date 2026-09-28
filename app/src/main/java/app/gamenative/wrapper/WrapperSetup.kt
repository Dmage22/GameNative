package app.gamenative.wrapper

import android.content.Context
import android.net.Uri
import app.gamenative.PrefManager
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.CustomGameScanner
import com.winlator.container.Container
import com.winlator.contents.AdrenotoolsManager
import com.winlator.contents.ContentProfile
import com.winlator.contents.ContentsManager
import com.winlator.core.WineRegistryEditor
import com.winlator.core.envvars.EnvVars
import com.winlator.fexcore.FEXCorePresetManager
import com.winlator.inputcontrols.InputControlsManager
import com.winlator.xenvironment.ImageFs
import com.winlator.xenvironment.ImageFsInstaller
import java.io.File
import org.json.JSONObject
import timber.log.Timber

/**
 * The wrapper's three steps, driven entirely by the preset (see docs/wrapper.md):
 * game files ([installFromPkg] / [registerGameFolder]), [setupEnvironment], then [prepareLaunch].
 */
object WrapperSetup {
    private const val STATE_PREFS = "wrapper_state"
    private const val KEY_GAME_FOLDER = "game_folder"
    private const val KEY_APP_ID = "app_id"
    private const val KEY_COMPONENTS_FOR_UPDATE = "components_installed_for_update"
    private const val KEY_CONTROLS_IMPORTED = "controls_profile_imported"
    private const val KEY_CONTROLS_PROFILE_ID = "controls_profile_id"
    private const val KEY_ENV_READY_FOR_UPDATE = "environment_ready_for_update"

    // Written by the GPU / Display dialogs (WrapperSettings).
    private const val SETTINGS_PREFS = "wrapper_settings"
    private const val DRIVER_SYSTEM = "system"

    fun gameFolder(context: Context): String? =
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getString(KEY_GAME_FOLDER, null)

    fun appId(context: Context): String? =
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getString(KEY_APP_ID, null)

    fun isGameInstalled(context: Context, preset: WrapperPreset): Boolean {
        val folder = gameFolder(context) ?: return false
        return File(folder, preset.install.exe).isFile
    }

    /** Registers an already-extracted game folder as the wrapper's custom game. Returns the appId. */
    fun registerGameFolder(context: Context, preset: WrapperPreset, folderPath: String): String {
        require(File(folderPath, preset.install.exe).isFile) { "${preset.install.exe} not found in $folderPath" }

        PrefManager.customGameManualFolders = PrefManager.customGameManualFolders + folderPath
        CustomGameScanner.invalidateCache()
        val item = CustomGameScanner.createLibraryItemFromFolder(folderPath)
            ?: throw IllegalStateException("Could not register $folderPath")

        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_GAME_FOLDER, folderPath)
            .putString(KEY_APP_ID, item.appId)
            .apply()
        return item.appId
    }

    /**
     * Imports an already-extracted game folder (picked with the system folder picker, no permission) into
     * the app's own storage and registers it. Blocking; call off the main thread.
     */
    fun importFolder(context: Context, preset: WrapperPreset, treeUri: Uri, onProgress: (Float) -> Unit): String {
        val target = File(context.getExternalFilesDir(null), "game")
        val gameDir = WrapperFolderImporter.import(context, treeUri, preset.install.exe, target, onProgress)
        return registerGameFolder(context, preset, gameDir.absolutePath)
    }

    /**
     * Installs the game from the Mac `.pkg` the user downloaded into the app's own storage (no storage
     * permission needed) and registers it. Blocking; call off the main thread.
     */
    fun installFromPkg(context: Context, preset: WrapperPreset, uri: Uri, onProgress: (Float) -> Unit): String {
        val resolver = context.contentResolver
        val size = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
        val target = File(context.getExternalFilesDir(null), "game")
        val gameDir = resolver.openInputStream(uri)?.use { input ->
            WineskinPkgInstaller.install(input, size, preset.install.exe, target, onProgress)
        } ?: throw java.io.IOException("Cannot open the selected file")
        return registerGameFolder(context, preset, gameDir.absolutePath)
    }

    /** True once [setupEnvironment] has completed for the installed app version. */
    fun isEnvironmentReady(context: Context): Boolean =
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getLong(KEY_ENV_READY_FOR_UPDATE, 0L) ==
            appUpdateTime(context)

    /**
     * The Setup step: installs the bundled components, creates the container from the preset, writes the
     * registry values and installs the base image, so Start game only has to launch. Blocking; call off
     * the main thread. Needs the game files first (the container maps A: to the game folder).
     */
    fun setupEnvironment(context: Context, preset: WrapperPreset, onStatus: (String) -> Unit = {}) {
        val appId = appId(context) ?: throw IllegalStateException("Choose the game files first")

        onStatus("Installing components…")
        installComponents(context, preset)
        ensureControlsProfile(context, preset)

        onStatus("Configuring…")
        val config = readAssetJson(context, preset.containerConfig)
        val fexPresetId = ensureFexPreset(context, preset)

        // Base system image first: a custom Proton's prefix is unpacked from imagefs/opt/<version>, which
        // only exists once the image is installed, so creating the container earlier fails on a fresh install.
        // The installer only reads the Wine version and variant, so a temporary container object is enough.
        // GameNative only writes the variant marker when a game boots, so write it here too, otherwise the
        // first launch would install the image again.
        val defaults = defaultsFrom(config, fexPresetId)
        val imageTarget = Container(appId).apply {
            wineVersion = defaults.wineVersion
            containerVariant = defaults.containerVariant
        }
        onStatus("Installing base system… 0%")
        val ok = ImageFsInstaller.installIfNeededFuture(context, context.assets, imageTarget) { percent ->
            onStatus("Installing base system… $percent%")
        }.get()
        if (!ok) throw IllegalStateException("Base system installation failed")
        ImageFs.find(context).createVariantFile(defaults.containerVariant)

        // New containers are created from the defaults, so set them first: the Wine prefix is
        // extracted for the container's wineVersion at creation time.
        if (!ContainerUtils.hasContainer(context, appId)) {
            ContainerUtils.setDefaultContainerData(defaults)
        }
        val container = ContainerUtils.getOrCreateContainer(context, appId)

        // Then load the full exported config on top (covers keys ContainerData doesn't, e.g. the
        // display renderer), followed by the wrapper's own values and the user's choices.
        config?.let { container.loadData(JSONObject(it.toString())) }
        fexPresetId?.let { container.setFEXCorePreset(it) }
        container.executablePath = preset.install.exe
        applyUserSettings(context, preset, container)
        linkControlsProfile(context, container)
        container.saveData()
        applyRegistry(container, preset)


        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_ENV_READY_FOR_UPDATE, appUpdateTime(context))
            .apply()
    }

    /** The Start game step: applies the player's GPU/Display/controls choices and returns the appId. */
    fun prepareLaunch(context: Context, preset: WrapperPreset): String {
        val appId = appId(context) ?: throw IllegalStateException("Choose the game files first")
        check(isEnvironmentReady(context)) { "Tap Setup first" }
        val container = ContainerUtils.getOrCreateContainer(context, appId)
        applyUserSettings(context, preset, container)
        linkControlsProfile(context, container)
        container.saveData()
        return appId
    }

    private fun appUpdateTime(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime

    private fun installComponents(context: Context, preset: WrapperPreset) {
        if (preset.components.isEmpty()) return
        // Components only change with the APK, so install them once per app update.
        val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        val appUpdated = appUpdateTime(context)
        if (state.getLong(KEY_COMPONENTS_FOR_UPDATE, 0L) == appUpdated) return

        val contentsManager = ContentsManager(context)
        for (name in preset.components) {
            val file = copyAsset(context, "wrapper/components/$name") ?: continue
            try {
                if (name.endsWith(".zip")) {
                    val installed = AdrenotoolsManager(context).installDriver(Uri.fromFile(file))
                    Timber.i("Wrapper: driver $name -> '${installed.ifEmpty { "already installed" }}'")
                } else {
                    installContent(context, contentsManager, file, name)
                }
            } finally {
                file.delete()
            }
        }
        contentsManager.syncContents()
        state.edit().putLong(KEY_COMPONENTS_FOR_UPDATE, appUpdated).apply()
    }

    private fun installContent(context: Context, contentsManager: ContentsManager, file: File, name: String) {
        contentsManager.extraContentFile(
            Uri.fromFile(file),
            object : ContentsManager.OnInstallFinishedCallback {
                override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                    Timber.e(e, "Wrapper: failed to read $name: $reason")
                }

                override fun onSucceed(profile: ContentProfile) {
                    contentsManager.finishInstallContent(
                        profile,
                        object : ContentsManager.OnInstallFinishedCallback {
                            override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                                if (reason == ContentsManager.InstallFailedReason.ERROR_EXIST) {
                                    Timber.i("Wrapper: $name already installed")
                                    ContentsManager.cleanTmpDir(context)
                                } else {
                                    Timber.e(e, "Wrapper: failed to install $name: $reason")
                                }
                            }

                            override fun onSucceed(profile: ContentProfile) {
                                Timber.i("Wrapper: installed $name (${profile.verName})")
                            }
                        },
                    )
                }
            },
        )
    }

    /** Imports the preset's controls profile once, so later user edits are kept. */
    private fun ensureControlsProfile(context: Context, preset: WrapperPreset) {
        if (preset.controlsProfile.isEmpty()) return
        val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        if (state.getBoolean(KEY_CONTROLS_IMPORTED, false)) return

        try {
            val json = context.assets.open("wrapper/${preset.controlsProfile}").bufferedReader().use {
                JSONObject(it.readText())
            }
            // importProfile numbers the new profile after the highest known id, so load the existing ones first.
            val manager = InputControlsManager(context).apply { getProfiles() }
            val profile = manager.importProfile(json) ?: return
            state.edit()
                .putBoolean(KEY_CONTROLS_IMPORTED, true)
                .putInt(KEY_CONTROLS_PROFILE_ID, profile.id)
                .apply()
        } catch (e: Exception) {
            Timber.e(e, "Wrapper: failed to import controls profile ${preset.controlsProfile}")
        }
    }

    /**
     * Points the container at the preset's layout unless the player picked another one. Without a
     * profileId, GameNative copies its controller profile 0 on first launch.
     */
    private fun linkControlsProfile(context: Context, container: Container) {
        val defaultId = defaultControlsProfileId(context) ?: return
        val current = container.getExtra("profileId", "0").toIntOrNull() ?: 0
        val manager = InputControlsManager(context)
        if (current == 0 || manager.getProfile(current) == null) {
            container.putExtra("profileId", defaultId.toString())
        }
    }

    fun defaultControlsProfileId(context: Context): Int? =
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_CONTROLS_PROFILE_ID, -1).takeIf { it != -1 }

    /** Creates the preset's FEX preset once (matched by name) and returns its id. */
    private fun ensureFexPreset(context: Context, preset: WrapperPreset): String? {
        val json = readAssetJson(context, preset.fexcorePreset) ?: return null
        val name = json.optString("name", preset.name)
        FEXCorePresetManager.getPresets(context).firstOrNull { it.name == name }?.let { return it.id }

        val envVars = EnvVars()
        val env = json.optJSONObject("env") ?: JSONObject()
        for (key in env.keys()) envVars.put(key, env.getString(key))
        return FEXCorePresetManager.editPreset(context, null, name, envVars)
    }

    private fun defaultsFrom(config: JSONObject?, fexPresetId: String?) =
        ContainerUtils.getDefaultContainerData().let { d ->
            if (config == null) return@let d
            fun s(key: String, fallback: String) = config.optString(key, fallback).ifEmpty { fallback }
            d.copy(
                wineVersion = s("wineVersion", d.wineVersion),
                containerVariant = s("containerVariant", d.containerVariant),
                emulator = s("emulator", d.emulator),
                fexcoreVersion = s("fexcoreVersion", d.fexcoreVersion),
                fexcorePreset = fexPresetId ?: d.fexcorePreset,
                dxwrapperConfig = s("dxwrapperConfig", d.dxwrapperConfig),
                graphicsDriver = s("graphicsDriver", d.graphicsDriver),
                graphicsDriverConfig = s("graphicsDriverConfig", d.graphicsDriverConfig),
                displayRenderer = s("displayRendererMode", d.displayRenderer),
                envVars = s("envVars", d.envVars),
                wincomponents = s("wincomponents", d.wincomponents),
                audioDriver = s("audioDriver", d.audioDriver),
                box64Version = s("box64Version", d.box64Version),
                box64Preset = s("box64Preset", d.box64Preset),
            ).also { it.dxwrapper = s("dxwrapper", d.dxwrapper) }
        }

    private fun applyUserSettings(context: Context, preset: WrapperPreset, container: Container) {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        container.screenSize = prefs.getString("screen_size", null) ?: preset.screenSize.ifEmpty { container.screenSize }
        container.putExtra("fpsLimiterEnabled", prefs.getBoolean("fps_limit_enabled", false))
        container.putExtra("fpsLimiterTarget", prefs.getInt("fps_limit_target", 60))
        if (prefs.getString("gpu_driver", null) == DRIVER_SYSTEM) {
            container.graphicsDriverConfig = replaceConfigValue(container.graphicsDriverConfig, "version", "System")
        }
    }

    private fun applyRegistry(container: Container, preset: WrapperPreset) {
        if (preset.registry.isEmpty()) return
        try {
            val userReg = File(container.rootDir, ".wine/user.reg")
            if (!userReg.isFile) {
                userReg.parentFile?.mkdirs()
                userReg.writeText("WINE REGISTRY Version 2\n\n")
            }
            WineRegistryEditor(userReg).use { editor ->
                editor.setCreateKeyIfNotExist(true)
                for (value in preset.registry) editor.setStringValue(value.key, value.name, value.value)
            }
        } catch (e: Exception) {
            Timber.e(e, "Wrapper: failed to write registry values")
        }
    }

    private fun replaceConfigValue(config: String, key: String, value: String): String {
        val parts = config.split(",").filter { it.isNotEmpty() }.toMutableList()
        val index = parts.indexOfFirst { it.startsWith("$key=") }
        if (index >= 0) parts[index] = "$key=$value" else parts.add("$key=$value")
        return parts.joinToString(",")
    }

    private fun readAssetJson(context: Context, name: String): JSONObject? {
        if (name.isEmpty()) return null
        return try {
            context.assets.open("wrapper/$name").bufferedReader().use { JSONObject(it.readText()) }
        } catch (e: Exception) {
            Timber.e(e, "Wrapper: cannot read asset $name")
            null
        }
    }

    private fun copyAsset(context: Context, path: String): File? = try {
        val out = File(context.cacheDir, path.substringAfterLast('/'))
        context.assets.open(path).use { input -> out.outputStream().use { input.copyTo(it) } }
        out
    } catch (e: Exception) {
        Timber.e(e, "Wrapper: bundled component missing: $path")
        null
    }
}
