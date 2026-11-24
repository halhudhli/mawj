package com.nuvio.app.features.player

import com.nuvio.app.features.luxo.LuxoFeaturesRepository
import com.nuvio.app.features.luxo.LuxoPrefsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * «ترميز الترجمة»: how subtitle bytes become text. Every subtitle path in the player (add-on
 * subtitles, AutoSync, downloads, translation) reads through [mawjFetchSubtitleText], so one
 * rule applies everywhere:
 *  - Auto: a valid UTF-8 (or UTF-16 with a BOM) file is kept exactly as it is; anything else is
 *    read as Arabic Windows-1256 when it is Arabic, otherwise by the general detector.
 *  - Utf8 / Arabic: forced by the viewer from the player menu when Auto guesses wrong.
 */
enum class MawjSubtitleEncodingMode(val key: String, val label: String) {
    Auto("auto", "تلقائي"),
    Utf8("utf8", "UTF-8"),
    Arabic("arabic", "عربي (Windows-1256)"),
}

object MawjSubtitleEncoding {
    private const val KEY = "mawj_subtitle_encoding"

    private val _mode = MutableStateFlow(
        runCatching { LuxoPrefsStorage.load(KEY) }.getOrNull()
            ?.let { saved -> MawjSubtitleEncodingMode.entries.firstOrNull { it.key == saved } }
            ?: MawjSubtitleEncodingMode.Auto,
    )
    val mode: StateFlow<MawjSubtitleEncodingMode> = _mode.asStateFlow()

    /** Goes up on every change, so the player re-reads the subtitle on screen. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** The switch «إصلاح ترميز الترجمة». Off: the original reading, no Mawj rules and no menu item. */
    fun enabled(): Boolean = runCatching { LuxoFeaturesRepository.current().subtitleEncodingFix }.getOrDefault(true)

    /** The mode in force right now (Auto when the switch is off). */
    fun effective(): MawjSubtitleEncodingMode = if (enabled()) _mode.value else MawjSubtitleEncodingMode.Auto

    fun set(mode: MawjSubtitleEncodingMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        runCatching { LuxoPrefsStorage.save(KEY, mode.key) }
        _revision.value = _revision.value + 1
    }
}

/** Arabic checks shared by every platform. */
object MawjArabicText {
    /** Bytes 0x80..0xFF of Windows-1256. */
    private const val CP1256_HIGH = "\u20AC\u067E\u201A\u0192\u201E\u2026\u2020\u2021\u02C6\u2030\u0679\u2039\u0152\u0686\u0698\u0688\u06AF\u2018\u2019\u201C\u201D\u2022\u2013\u2014\u06A9\u2122\u0691\u203A\u0153\u200C\u200D\u06BA\u00A0\u060C\u00A2\u00A3\u00A4\u00A5\u00A6\u00A7\u00A8\u00A9\u06BE\u00AB\u00AC\u00AD\u00AE\u00AF\u00B0\u00B1\u00B2\u00B3\u00B4\u00B5\u00B6\u00B7\u00B8\u00B9\u061B\u00BB\u00BC\u00BD\u00BE\u061F\u06C1\u0621\u0622\u0623\u0624\u0625\u0626\u0627\u0628\u0629\u062A\u062B\u062C\u062D\u062E\u062F\u0630\u0631\u0632\u0633\u0634\u0635\u0636\u00D7\u0637\u0638\u0639\u063A\u0640\u0641\u0642\u0643\u00E0\u0644\u00E2\u0645\u0646\u0647\u0648\u00E7\u00E8\u00E9\u00EA\u00EB\u0649\u064A\u00EE\u00EF\u064B\u064C\u064D\u064E\u00F4\u064F\u0650\u00F7\u0651\u00F9\u0652\u00FB\u00FC\u200E\u200F\u06D2"

    fun isArabicLetter(c: Char): Boolean = c in '\u0620'..'\u064A' || c in '\u066E'..'\u06D3' || c in '\u06FA'..'\u06FF'

    /** Share of letters that are Arabic, 0..1. */
    fun arabicShare(text: String, sample: Int = 6_000): Float {
        var letters = 0
        var arabic = 0
        for (c in text.take(sample)) {
            if (!c.isLetter()) continue
            letters++
            if (isArabicLetter(c)) arabic++
        }
        return if (letters == 0) 0f else arabic.toFloat() / letters
    }

    /** True when the text itself is Arabic (whatever its language tag says). */
    fun looksArabic(text: String): Boolean = arabicShare(text) >= 0.3f

    /** Windows-1256 for one byte. */
    fun cp1256(byte: Int): Char = if (byte < 0x80) byte.toChar() else CP1256_HIGH[byte - 0x80]

    /** Windows-1256 for a whole byte array (used where no platform charset exists). */
    fun decodeCp1256(bytes: ByteArray): String = buildString(bytes.size) {
        for (b in bytes) append(cp1256(b.toInt() and 0xFF))
    }

    /**
     * Repairs Arabic that was read as Latin-1 / Windows-1252 somewhere on the way («ÇáÓáÇã»).
     * Only when the text has almost no Arabic, the Latin-1 letters outnumber the plain Latin ones
     * (so French or Spanish accents are never touched) and the repaired text is clearly Arabic.
     */
    fun repairLatin1Mojibake(text: String): String {
        if (text.isEmpty()) return text
        var arabic = 0
        var high = 0
        var ascii = 0
        for (c in text.take(20_000)) {
            when {
                isArabicLetter(c) -> arabic++
                c.code in 0xC0..0xFF -> high++
                c in 'a'..'z' || c in 'A'..'Z' -> ascii++
                c.code > 0xFF && c.isLetter() -> return text
            }
        }
        if (arabic >= 5 || high < 20 || high <= ascii) return text
        val win1252 = mapOf(
            '\u20AC' to 0x80, '\u201A' to 0x82, '\u0192' to 0x83, '\u201E' to 0x84, '\u2026' to 0x85,
            '\u2020' to 0x86, '\u2021' to 0x87, '\u02C6' to 0x88, '\u2030' to 0x89, '\u0160' to 0x8A,
            '\u2039' to 0x8B, '\u0152' to 0x8C, '\u017D' to 0x8E, '\u2018' to 0x91, '\u2019' to 0x92,
            '\u201C' to 0x93, '\u201D' to 0x94, '\u2022' to 0x95, '\u2013' to 0x96, '\u2014' to 0x97,
            '\u02DC' to 0x98, '\u2122' to 0x99, '\u0161' to 0x9A, '\u203A' to 0x9B, '\u0153' to 0x9C,
            '\u017E' to 0x9E, '\u0178' to 0x9F,
        )
        val repaired = buildString(text.length) {
            for (c in text) {
                val byte = when {
                    c.code in 0x80..0xFF -> c.code
                    else -> win1252[c]
                }
                append(if (byte != null) cp1256(byte) else c)
            }
        }
        return if (arabicShare(repaired) >= 0.5f) repaired else text
    }
}

/** The one decoding rule. [fallback] is the platform's general detector (other languages). */
object MawjSubtitleDecode {
    private fun isArabicHint(hint: String?): Boolean {
        val h = hint?.trim()?.lowercase().orEmpty()
        val lang = h.substringBefore('-').substringBefore('_')
        return lang == "ar" || lang == "ara" || h.startsWith("arabic") || h.startsWith("عرب")
    }

    fun isValidUtf8(bytes: ByteArray, from: Int = 0): Boolean =
        runCatching { bytes.decodeToString(from, bytes.size, throwOnInvalidSequence = true) }.isSuccess

    fun decode(bytes: ByteArray, languageHint: String?, fallback: (ByteArray, String?) -> String): String {
        if (bytes.isEmpty()) return ""
        if (!MawjSubtitleEncoding.enabled()) return fallback(bytes, languageHint)
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes.getOrNull(1)?.toInt()?.and(0xFF) ?: -1
        val b2 = bytes.getOrNull(2)?.toInt()?.and(0xFF) ?: -1
        // UTF-16 with a byte-order mark: the platform reads it.
        if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) return fallback(bytes, languageHint)
        val utf8Start = if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) 3 else 0
        return when (MawjSubtitleEncoding.effective()) {
            MawjSubtitleEncodingMode.Utf8 -> bytes.decodeToString(utf8Start, bytes.size)
            MawjSubtitleEncodingMode.Arabic ->
                if (utf8Start == 3) bytes.decodeToString(3, bytes.size) else MawjArabicText.decodeCp1256(bytes)
            MawjSubtitleEncodingMode.Auto -> when {
                // A valid UTF-8 file is never re-encoded; only Latin-1 damage is repaired.
                utf8Start == 3 || isValidUtf8(bytes) ->
                    MawjArabicText.repairLatin1Mojibake(bytes.decodeToString(utf8Start, bytes.size))
                isArabicHint(languageHint) -> MawjArabicText.decodeCp1256(bytes)
                MawjArabicText.looksArabic(MawjArabicText.decodeCp1256(bytes.copyOf(minOf(bytes.size, 8_192)))) ->
                    MawjArabicText.decodeCp1256(bytes)
                else -> fallback(bytes, languageHint)
            }
        }
    }
}

/**
 * Downloads (or reads, for file: addresses) a subtitle and returns its text decoded by the Mawj
 * rules above. Throws like the plain HTTP helpers when it can't be read.
 */
expect suspend fun mawjFetchSubtitleText(
    url: String,
    headers: Map<String, String> = emptyMap(),
    languageHint: String? = null,
): String
