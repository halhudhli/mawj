package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
import nuvio.composeapp.generated.resources.luxo_barlow_bold
import nuvio.composeapp.generated.resources.luxo_barlow_regular
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
    BARLOW("barlow", "Barlow", false, Res.font.luxo_barlow_regular, Res.font.luxo_barlow_bold, "luxo_barlow_regular.ttf"),
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
    /** Arabic letters. "default" keeps the system Arabic font. */
    val arabicFont: String = LuxoFontChoice.DEFAULT,
    /** Latin letters (titles, descriptions in English). Barlow: a clean cinematic sans. */
    val englishFont: String = LuxoBundledFont.BARLOW.id,
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

/**
 * The app-wide font family, or null for the app's original font.
 *
 * On Android 10+ this is one family that takes Latin letters from the English font and Arabic
 * letters from the Arabic font, so changing one never changes the other. Elsewhere (or while it
 * is still loading) it falls back to the single font of the current app language.
 */
@Composable
internal fun luxoSelectedAppFontFamily(): FontFamily? {
    val settings by remember {
        LuxoFontSettingsRepository.ensureLoaded()
        LuxoFontSettingsRepository.state
    }.collectAsStateWithLifecycle()
    val scriptFamily by LuxoScriptFontRuntime.family.collectAsStateWithLifecycle()
    LaunchedEffect(settings) { LuxoScriptFontRuntime.request(settings) }
    scriptFamily?.let { (builtFor, family) -> if (builtFor == settings) return family }

    val choice = if (Locale.current.language == "ar") settings.arabicFont else settings.englishFont
    if (choice == LuxoFontChoice.DEFAULT) return null
    return luxoFontFamilyFor(choice)
}

/** Font files on disk for one script: a regular face and, when available, a bold one. */
data class LuxoFontFiles(val regular: String, val bold: String?)

/** True where one family can mix two fonts by script (Android 10+). */
internal expect fun luxoScriptFontsSupported(): Boolean

/** Absolute path of a cached font file, or null when it isn't cached yet. */
internal expect fun luxoFontCachePath(name: String): String?

/** Writes a font into the app's font cache and returns its path. */
internal expect fun luxoWriteFontCache(name: String, bytes: ByteArray): String?

/** Latin from [latin], Arabic from [arabic] (system Arabic font when null). */
internal expect fun luxoBuildScriptFontFamily(latin: LuxoFontFiles, arabic: LuxoFontFiles?): FontFamily?

internal object LuxoScriptFontRuntime {
    private val _family = MutableStateFlow<Pair<LuxoFontSettings, FontFamily>?>(null)
    val family: StateFlow<Pair<LuxoFontSettings, FontFamily>?> = _family.asStateFlow()
    private var requested: LuxoFontSettings? = null
    private val mutex = Mutex()

    suspend fun request(settings: LuxoFontSettings) {
        if (!luxoScriptFontsSupported()) return
        if (requested == settings) return
        requested = settings
        val built = withContext(Dispatchers.Default) {
            mutex.withLock { runCatching { build(settings) }.getOrNull() }
        }
        if (requested == settings && built != null) _family.value = settings to built
    }

    private suspend fun build(settings: LuxoFontSettings): FontFamily? {
        val latin = filesFor(settings.englishFont, isLatin = true) ?: return null
        val arabic = filesFor(settings.arabicFont, isLatin = false)
        return luxoBuildScriptFontFamily(latin, arabic)
    }

    private suspend fun filesFor(choice: String, isLatin: Boolean): LuxoFontFiles? {
        if (choice == LuxoFontChoice.DEFAULT) {
            return if (isLatin) bundled("jetbrains_sans_regular.ttf", "jetbrains_sans_bold.ttf") else null
        }
        LuxoBundledFont.byId(choice)?.let { font ->
            return bundled(font.resourceFile, font.resourceFile.replace("_regular", "_bold"))
        }
        val fileName = LuxoFontChoice.customFileOf(choice) ?: return null
        val path = SubtitleFontRepository.pathOf(fileName) ?: return null
        return LuxoFontFiles(regular = path, bold = null)
    }

    private suspend fun bundled(regular: String, bold: String): LuxoFontFiles? {
        val regularPath = cached(regular) ?: return null
        return LuxoFontFiles(regular = regularPath, bold = cached(bold))
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun cached(name: String): String? {
        luxoFontCachePath(name)?.let { return it }
        val bytes = runCatching { Res.readBytes("font/$name") }.getOrNull() ?: return null
        return luxoWriteFontCache(name, bytes)
    }
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
