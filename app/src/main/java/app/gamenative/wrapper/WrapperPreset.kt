package app.gamenative.wrapper

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

@Serializable
data class WrapperPreset(
    val id: String,
    val name: String,
    val install: WrapperInstall,
    val components: List<String> = emptyList(),
    val container: JsonObject = JsonObject(emptyMap()),
    val screenSize: String = "",
    val registry: List<WrapperRegistryValue> = emptyList(),
    val controlsProfile: String = "",
    val configFile: String = "",
    val background: String = "",
)

@Serializable
data class WrapperInstall(
    val type: String,
    val gameDir: String = "",
    val exe: String,
)

@Serializable
data class WrapperRegistryValue(
    val key: String,
    val name: String,
    val value: String,
)

object WrapperPresetLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): WrapperPreset? {
        return try {
            context.assets.open("wrapper/preset.json").bufferedReader().use { reader ->
                json.decodeFromString<WrapperPreset>(reader.readText())
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load wrapper preset")
            null
        }
    }
}
object WrapperPaths {
    /** Folder the game gets installed into (app-specific storage, visible to Wine as the A: drive). */
    fun gameDir(context: Context, preset: WrapperPreset): java.io.File =
        java.io.File(context.getExternalFilesDir(null), "game/${preset.install.gameDir.ifEmpty { preset.id }}")
}
