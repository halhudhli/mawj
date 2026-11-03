package com.nuvio.app.features.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_no_subtitles_found
import org.jetbrains.compose.resources.getString

object SubtitleRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _addonSubtitles = MutableStateFlow<List<AddonSubtitle>>(emptyList())
    val addonSubtitles: StateFlow<List<AddonSubtitle>> = _addonSubtitles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingProgress = MutableStateFlow<SubtitleLoadingProgress?>(null)
    internal val loadingProgress: StateFlow<SubtitleLoadingProgress?> = _loadingProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var activeFetchJob: Job? = null

    fun fetchAddonSubtitles(type: String, videoId: String) {
        activeFetchJob?.cancel()
        _loadingProgress.value = null
        activeFetchJob = scope.launch {
            _isLoading.value = true
            _error.value = null
            _addonSubtitles.value = emptyList()

            val englishCandidates = MutableStateFlow<List<AddonSubtitle>>(emptyList())
            val requests = addonSubtitleRequests(type, videoId)
            // LUXO: SubDL + add-ons, Arabic only
            _loadingProgress.value = SubtitleLoadingProgress(total = requests.size + 1)
            if (requests.isNotEmpty()) {
                loadAddonSubtitles(requests) { request, subtitles ->
                    _addonSubtitles.update { it + subtitles.filter(LuxoSubtitleProviders::isArabic) }
                    englishCandidates.update { it + subtitles.filter(LuxoSubtitleTranslator::isEnglish) }
                    _loadingProgress.update { progress ->
                        progress?.copy(completed = progress.completed + 1, addonName = request.addonName)
                    }
                }
            }
            val builtIn = LuxoSubtitleProviders.search(type, videoId)
            _addonSubtitles.update { current -> (current + builtIn).distinctBy { it.url } }
            _loadingProgress.update { progress ->
                progress?.copy(completed = progress.completed + 1, addonName = "SubDL")
            }

            // LUXO: no Arabic subtitle → translate an English one with Luxo Companion.
            val shouldTranslate = _addonSubtitles.value.isEmpty() &&
                englishCandidates.value.isNotEmpty() &&
                LuxoSubtitleTranslator.canTranslate()
            if (shouldTranslate) {
                _loadingProgress.update { progress ->
                    progress?.copy(addonName = "ترجمة موج ✨")
                }
                val translated = englishCandidates.value.take(2).firstNotNullOfOrNull { LuxoSubtitleTranslator.translate(it) }
                if (translated != null) _addonSubtitles.update { it + translated }
            }

            if (_addonSubtitles.value.isEmpty()) {
                _error.value = getString(Res.string.compose_player_no_subtitles_found)
            }
            _isLoading.value = false
        }
    }

    fun clear() {
        activeFetchJob?.cancel()
        _loadingProgress.value = null
        _addonSubtitles.value = emptyList()
        _isLoading.value = false
        _error.value = null
    }
}

internal data class SubtitleLoadingProgress(
    val total: Int,
    val completed: Int = 0,
    val addonName: String? = null,
)
