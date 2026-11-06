package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** On/off switches for Luxo's extra features. Everything can be turned off. */
@Serializable
data class LuxoFeatures(
    val luxoDesign: Boolean = true,
    val luxoDock: Boolean = true,
    /** 0 = no glow, 1 = soft (default), 2 = strong. */
    val glowLevel: Int = 1,
    val trailerFit: Boolean = true,
    val heroTilt: Boolean = true,
    val timeTint: Boolean = true,
    val countdown: Boolean = true,
    val diary: Boolean = true,
    val challenge: Boolean = true,
    val wrapped: Boolean = true,
    val profileHub: Boolean = true,
    val passTilt: Boolean = true,
    val seasonalThemes: Boolean = true,
    val achievements: Boolean = true,
    val heatmap: Boolean = true,
    val shareCard: Boolean = true,
    val haptics: Boolean = true,
    val discoveryQueue: Boolean = true,
    val animeSection: Boolean = true,
    val themeStudio: Boolean = true,
    val playerScreenshot: Boolean = true,
    val playerAbLoop: Boolean = true,
    val ambientGlow: Boolean = false,
    val streamRanking: Boolean = true,
    val hideCamReleases: Boolean = true,
    /** TV only: lighter Home (smaller pictures, fewer animations, no autoplay trailer). */
    val tvLite: Boolean = true,
    /** TV only: focus frames and smooth focus scrolling for the remote. */
    val tvRemote: Boolean = true,
    /** Picture colours in the player (ready looks + manual tuning). */
    val videoColors: Boolean = true,
    /** Details page: TMDB gallery, extra videos and soundtrack. */
    val mediaHub: Boolean = true,
    /** The redesigned actor page. */
    val personPage: Boolean = true,
    /** Translate non-Arabic overviews/biographies and write people's names in Arabic. */
    val arabicTranslate: Boolean = true,
    /** Player: if the picture freezes while data is buffered, recover it automatically. */
    val stallGuard: Boolean = true,
    /** «السلاسل»: franchises and watch order. */
    val franchises: Boolean = true,
    /** Dark glass cards for channels / companies and the new company page. */
    val companiesRedesign: Boolean = true,
    /** Save gallery images to the device. */
    val saveImages: Boolean = true,
    /** Player: «بكرة الفيلم» seek preview, fine scrubbing and 10-second ticks. */
    val filmReel: Boolean = true,
    /** Player: translate a subtitle to Arabic with Gemini. */
    val subtitleTranslate: Boolean = true,
    /** Player: saved moments with notes. */
    val bookmarks: Boolean = true,
    /** Player: dialogue boost and night audio. */
    val audioFx: Boolean = true,
    /** Player: share the frame with the subtitle line. */
    val shareScene: Boolean = true,
    /** «نبهني لما ينزل» and followed-actor notifications. */
    val releaseReminders: Boolean = true,
    /** Search by describing a title. */
    val describeSearch: Boolean = true,
    /** Compare two titles side by side. */
    val compareTitles: Boolean = true,
    /** Anime openings / endings video in the media section. */
    val animeThemes: Boolean = true,
    /** «ذوقي» Bento board. */
    val tasteBento: Boolean = true,
    /** Time left to finish a series + a viewing plan. */
    val timeLeft: Boolean = true,
    /** Per-series audio/subtitle/speed memory. */
    val seriesMemory: Boolean = true,
    /** Private mode (nothing recorded while it's on) — the switch only shows the option. */
    val privateMode: Boolean = true,
    /** «خمّن العمل» game. */
    val guessGame: Boolean = true,
    /** Screensaver after idle time. */
    val screensaver: Boolean = true,
    /** «لأنه تمت مشاهدة…» rows on the home. */
    val becauseWatched: Boolean = true,
    /** Spoiler-free character guide. */
    val characterGuide: Boolean = true,
    /** «سابقًا في…» before an episode after a long break. */
    val previouslyOn: Boolean = true,
    /** «أين قيلت هذه العبارة؟». */
    val quoteFinder: Boolean = true,
    /** Saved subtitle lines. */
    val quotes: Boolean = true,
    /** Animated clip (GIF) from the player. */
    val gifClip: Boolean = true,
    /** Smart source choice. */
    val smartSource: Boolean = true,
    /** Phone/tablet as a remote for the TV, and «أكمل على الشاشة». */
    val remote: Boolean = true,
    /** Series status alerts (renewed, cancelled, finale). */
    val seriesStatus: Boolean = true,
    /** Add episode dates to the device calendar. */
    val calendarAdd: Boolean = true,
    /** Identify a title from an image. */
    val imageSearch: Boolean = true,
    /** Family choice wheel. */
    val choiceWheel: Boolean = true,
    /** Kids profile (age-rating gate). */
    val kidsProfile: Boolean = true,
    /** «يوتيوب» hall: the official site inside the app (the official app on TV). */
    val youtube: Boolean = true,
    /** «شرح النهاية» in the companion (asks before the spoiler). */
    val endingExplained: Boolean = true,
    /** «خريطة تقييم الحلقات» on series pages. */
    val episodeMap: Boolean = true,
    /** «ما بدأته ولم يكتمل» hall. */
    val unfinished: Boolean = true,
    /** «من في المشهد؟» in the player menu. */
    val whoInScene: Boolean = true,
    /** Daily watch-time limit for the kids profile. */
    val kidsTimeLimit: Boolean = true,
    /** Voice questions in the companion, with the answer read aloud. */
    val companionVoice: Boolean = true,
    /** «حشو» badges on anime episodes. */
    val fillerGuide: Boolean = true,
    /** Autoplay passes over filler episodes. */
    val fillerSkip: Boolean = false,
    /** «الموسيقى التصويرية» في «المزيد عن العمل» */
    val soundtrack: Boolean = true,
    /** «مواقع التصوير» */
    val filmingLocations: Boolean = true,
    /** «هل تعلم؟» */
    val trivia: Boolean = true,
    /** «بين الرواية والشاشة» */
    val bookVsScreen: Boolean = true,
    /** «أين يُعرض رسمياً؟» (TMDB) */
    val whereToWatch: Boolean = true,
    /** «دليل المحتوى للأهل» */
    val parentsGuide: Boolean = true,
    /** «ملاحظاتي» */
    val personalNotes: Boolean = true,
    /** «شاهدته خارج موج» */
    val watchedOutside: Boolean = true,
    /** «كم عندي وقت؟» */
    val timeBudget: Boolean = true,
    /** Rating prompt after a movie or a series finale */
    val rateAfter: Boolean = true,
    /** Seasonal anime calendar (AniList) */
    val animeSeason: Boolean = true,
    /** «ماذا نشاهد معاً؟» */
    val watchTogether: Boolean = true,
    /** «اختبار الحلقة» in the player menu */
    val episodeQuiz: Boolean = true,
    /** «ذكريات المشاهدة» notifications */
    val watchMemories: Boolean = true,
    /** «ترتيبي الخاص» */
    val tierList: Boolean = true,
    /** «اقتراح المساء» notification */
    val eveningPick: Boolean = true,
    /** Google TV «أكمل المشاهدة» row */
    val googleTvRow: Boolean = true,
    /** Nearest upcoming episode on the home-screen widget */
    val widgetNext: Boolean = true,
    /** «الشاشة الثانية» on the tablet */
    val secondScreen: Boolean = true,
    /** «خلفية من المشهد» in the player menu */
    val sceneWallpaper: Boolean = true,
    /** Big downloads split over several connections (much faster on debrid servers). */
    val fastDownload: Boolean = true,
    /** «بطاقة عضوية موج» at the top of the profile page. */
    val membershipCard: Boolean = true,
    /** Ads are blocked and skipped inside Mawj's YouTube page. */
    val youtubeAdBlock: Boolean = true,
    /** «يوتيوب» opens ReVanced / SmartTube when installed. */
    val youtubeAdFreeApp: Boolean = true,
    /** «تقدّمك في أعماله» */
    val actorProgress: Boolean = true,
    /** «من أين أبدأ؟» */
    val actorStartHere: Boolean = true,
    /** «ماراثون الممثل» */
    val actorMarathon: Boolean = true,
    /** «صوره عبر السنين» */
    val actorPhotosYears: Boolean = true,
    /** «الأدوار الصوتية» */
    val actorVoiceRoles: Boolean = true,
    /** «قارن ممثلين» */
    val actorCompare: Boolean = true,
    /** «الدرجات الست» */
    val actorSixDegrees: Boolean = true,
    /** «من أي عمل هذه اللقطة؟» */
    val actorStillQuiz: Boolean = true,
    /** «مسيرته حول العالم» */
    val actorWorldMap: Boolean = true,
    /** «حقائق سريعة» */
    val actorFacts: Boolean = true,
    /** «تذكير بعيد ميلاده» */
    val actorBirthday: Boolean = true,
    /** Actor details from the taste board */
    val tasteActorDetail: Boolean = true,
    /** «قاعة العرض» company page */
    val companyHall: Boolean = true,
    /** Animated catalog cards */
    val animatedCatalog: Boolean = true,
)

object LuxoFeaturesRepository {
    private const val storageKey = "features_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(LuxoFeatures())
    val state: StateFlow<LuxoFeatures> = _state.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val payload = LuxoPrefsStorage.load(storageKey) ?: return
        runCatching { json.decodeFromString(LuxoFeatures.serializer(), payload) }
            .onSuccess { _state.value = it }
    }

    fun current(): LuxoFeatures {
        ensureLoaded()
        return _state.value
    }

    fun update(transform: (LuxoFeatures) -> LuxoFeatures) {
        ensureLoaded()
        val next = transform(_state.value)
        _state.value = next
        LuxoPrefsStorage.save(storageKey, json.encodeToString(LuxoFeatures.serializer(), next))
    }
}

@Composable
fun rememberLuxoFeatures(): LuxoFeatures {
    val state by remember {
        LuxoFeaturesRepository.ensureLoaded()
        LuxoFeaturesRepository.state
    }.collectAsStateWithLifecycle()
    return state
}
