package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.player.SubtitleFontFiles
import com.nuvio.app.features.player.SubtitleFontRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.luxo_almarai_bold
import nuvio.composeapp.generated.resources.luxo_almarai_regular
import nuvio.composeapp.generated.resources.luxo_lato_bold
import nuvio.composeapp.generated.resources.luxo_lato_regular
import nuvio.composeapp.generated.resources.luxo_plexarabic_bold
import nuvio.composeapp.generated.resources.luxo_plexarabic_regular
import nuvio.composeapp.generated.resources.luxo_poppins_bold
import nuvio.composeapp.generated.resources.luxo_poppins_regular
import nuvio.composeapp.generated.resources.luxo_tajawal_bold
import nuvio.composeapp.generated.resources.luxo_tajawal_regular
import nuvio.composeapp.generated.resources.luxo_ubuntu_bold
import nuvio.composeapp.generated.resources.luxo_ubuntu_regular
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

/** Fonts shipped inside the app. Arabic ones also contain Latin letters. */
enum class LuxoBundledFont(
    val id: String,
    val displayName: String,
    val isArabic: Boolean,
    val regular: FontResource,
    val bold: FontResource,
    /** Name of the regular file inside composeResources/font, copied into the shared font folder. */
    val resourceFile: String,
) {
    TAJAWAL("tajawal", "Tajawal · تجوّل", true, Res.font.luxo_tajawal_regular, Res.font.luxo_tajawal_bold, "luxo_tajawal_regular.ttf"),
    ALMARAI("almarai", "Almarai · المراعي", true, Res.font.luxo_almarai_regular, Res.font.luxo_almarai_bold, "luxo_almarai_regular.ttf"),
    PLEX_ARABIC("plexarabic", "IBM Plex Arabic · بلكس", true, Res.font.luxo_plexarabic_regular, Res.font.luxo_plexarabic_bold, "luxo_plexarabic_regular.ttf"),
    POPPINS("poppins", "Poppins", false, Res.font.luxo_poppins_regular, Res.font.luxo_poppins_bold, "luxo_poppins_regular.ttf"),
    LATO("lato", "Lato", false, Res.font.luxo_lato_regular, Res.font.luxo_lato_bold, "luxo_lato_regular.ttf"),
    UBUNTU("ubuntu", "Ubuntu", false, Res.font.luxo_ubuntu_regular, Res.font.luxo_ubuntu_bold, "luxo_ubuntu_regular.ttf"),
    ;

    companion object {
        fun byId(id: String?): LuxoBundledFont? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The font choice for one script. Stored as a string id:
 * "default" (the app's original font), a [LuxoBundledFont.id], or "custom:<fileName>" for a font
 * the user imported (kept in the shared font folder that subtitles use too).
 */
object LuxoFontChoice {
    const val DEFAULT = "default"
    private const val CUSTOM_PREFIX = "custom:"

    fun custom(fileName: String) = CUSTOM_PREFIX + fileName
    fun customFileOf(choice: String): String? =
        choice.takeIf { it.startsWith(CUSTOM_PREFIX) }?.removePrefix(CUSTOM_PREFIX)
}

@Serializable
data class LuxoFontSettings(
    /** Used while the app language is Arabic. */
    val arabicFont: String = LuxoFontChoice.DEFAULT,
    /** Used for every other app language. */
    val englishFont: String = LuxoFontChoice.DEFAULT,
)

object LuxoFontSettingsRepository {
    private const val storageKey = "font_settings_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(LuxoFontSettings())
    val state: StateFlow<LuxoFontSettings> = _state.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val payload = LuxoPrefsStorage.load(storageKey) ?: return
        runCatching { json.decodeFromString(LuxoFontSettings.serializer(), payload) }
            .onSuccess { _state.value = it }
    }

    fun update(transform: (LuxoFontSettings) -> LuxoFontSettings) {
        ensureLoaded()
        val next = transform(_state.value)
        _state.value = next
        LuxoPrefsStorage.save(storageKey, json.encodeToString(LuxoFontSettings.serializer(), next))
    }

    /** Called when an imported font is deleted, so the app never points at a missing file. */
    fun forgetCustom(fileName: String) = update { settings ->
        val gone = LuxoFontChoice.custom(fileName)
        settings.copy(
            arabicFont = if (settings.arabicFont == gone) LuxoFontChoice.DEFAULT else settings.arabicFont,
            englishFont = if (settings.englishFont == gone) LuxoFontChoice.DEFAULT else settings.englishFont,
        )
    }
}

/** Builds a font family from an imported font file. Null when the platform can't load it. */
internal expect fun luxoFontFamilyFromFile(path: String): FontFamily?

private val customFamilyCache = mutableMapOf<String, FontFamily?>()

internal fun customFontFamily(fileName: String): FontFamily? {
    val path = SubtitleFontRepository.pathOf(fileName) ?: return null
    return customFamilyCache.getOrPut(path) { runCatching { luxoFontFamilyFromFile(path) }.getOrNull() }
}

/** Font family for a stored choice, or null for "use the app's original font". */
@Composable
internal fun luxoFontFamilyFor(choice: String): FontFamily? {
    LuxoBundledFont.byId(choice)?.let { font ->
        return FontFamily(
            Font(font.regular, FontWeight.Normal, FontStyle.Normal),
            Font(font.regular, FontWeight.Medium, FontStyle.Normal),
            Font(font.bold, FontWeight.SemiBold, FontStyle.Normal),
            Font(font.bold, FontWeight.Bold, FontStyle.Normal),
        )
    }
    val file = LuxoFontChoice.customFileOf(choice) ?: return null
    return remember(file) { customFontFamily(file) }
}

/** The app-wide font family the user picked for the current app language, or null for default. */
@Composable
internal fun luxoSelectedAppFontFamily(): FontFamily? {
    val settings by remember {
        LuxoFontSettingsRepository.ensureLoaded()
        LuxoFontSettingsRepository.state
    }.collectAsStateWithLifecycle()
    val choice = if (Locale.current.language == "ar") settings.arabicFont else settings.englishFont
    if (choice == LuxoFontChoice.DEFAULT) return null
    return luxoFontFamilyFor(choice)
}

/**
 * Copies the bundled fonts into the shared font folder (once), so they also show up in the
 * subtitle font list for both players. Safe to call repeatedly; does blocking I/O.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun installBundledFontsForSubtitles() {
    // Only once per install, so a font the user deletes from the list doesn't come back.
    if (LuxoPrefsStorage.load(BUNDLED_FONTS_INSTALLED_KEY) == "1") return
    if (SubtitleFontFiles.directoryPath() == null) return
    val existing = SubtitleFontFiles.listFileNames().toSet()
    var changed = false
    LuxoBundledFont.entries.forEach { font ->
        if (font.resourceFile in existing) return@forEach
        val bytes = runCatching { Res.readBytes("font/${font.resourceFile}") }.getOrNull() ?: return@forEach
        if (SubtitleFontFiles.write(font.resourceFile, bytes)) changed = true
    }
    if (changed) SubtitleFontRepository.refresh()
    LuxoPrefsStorage.save(BUNDLED_FONTS_INSTALLED_KEY, "1")
}

private const val BUNDLED_FONTS_INSTALLED_KEY = "bundled_fonts_installed_v1"
