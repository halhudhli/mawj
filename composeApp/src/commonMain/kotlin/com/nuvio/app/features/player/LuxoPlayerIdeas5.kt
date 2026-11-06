package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.luxo.LuxoKidsTime
import com.nuvio.app.features.luxo.LuxoKidsTimeUpDialog
import com.nuvio.app.features.luxo.LuxoWhoInScene
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** «من في المشهد؟» and the kids daily limit. */
@Composable
internal fun LuxoIdeas5PlayerHooks(
    controller: PlayerEngineController?,
    title: String,
    season: Int?,
    episode: Int?,
    isPlaying: Boolean,
    metaId: String,
    metaType: String,
) {
    val features = rememberLuxoFeatures()
    val scope = rememberCoroutineScope()
    val currentController by rememberUpdatedState(controller)

    // ---- «من في المشهد؟»
    var whoOpen by remember { mutableStateOf(false) }
    var whoAnswer by remember { mutableStateOf<String?>(null) }
    DisposableEffect(features.whoInScene, metaId) {
        LuxoPlayerBridge.onWhoInSceneClick = if (features.whoInScene) ({
            val key = LuxoCompanionApi.loadKey()
            if (key == null) {
                scope.launch {
                    LuxoPlayerBridge.toast = "يلزم مفتاح Gemini أولاً (من رفيق موج في صفحة العمل)"
                    delay(3_000L); LuxoPlayerBridge.toast = null
                }
            } else {
                currentController?.pause()
                whoOpen = true
                whoAnswer = null
                scope.launch {
                    val jpeg = luxoCaptureVideoJpeg(1280, 720)
                    if (jpeg == null) {
                        whoAnswer = "تعذّر التقاط هذا المشهد (بعض المصادر محمية)"
                        return@launch
                    }
                    val cast = runCatching { LuxoWhoInScene.castLines(metaId, metaType, season, episode) }.getOrDefault(emptyList())
                    whoAnswer = LuxoCompanionApi.ask(
                        key,
                        LuxoWhoInScene.prompt(title, season, episode, cast),
                        image = "image/jpeg" to kotlin.io.encoding.Base64.encode(jpeg),
                    )
                }
            }
        }) else null
        onDispose { LuxoPlayerBridge.onWhoInSceneClick = null }
    }
    if (whoOpen) {
        AlertDialog(
            onDismissRequest = { whoOpen = false },
            title = { Text("👤 من في المشهد؟") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val a = whoAnswer
                    if (a == null) {
                        CircularProgressIndicator()
                        Text("يُتعرَّف على من في اللقطة…")
                    } else {
                        Text(a)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { whoOpen = false; currentController?.play() }) { Text("متابعة المشاهدة") }
            },
            dismissButton = { TextButton(onClick = { whoOpen = false }) { Text("إغلاق") } },
        )
    }

    // ---- «اختبار الحلقة»
    var quizOpen by remember { mutableStateOf(false) }
    DisposableEffect(features.episodeQuiz, metaId) {
        LuxoPlayerBridge.onQuizClick = if (features.episodeQuiz) ({ currentController?.pause(); quizOpen = true }) else null
        onDispose { LuxoPlayerBridge.onQuizClick = null }
    }
    if (quizOpen) com.nuvio.app.features.luxo.LuxoQuizDialog(title, season, episode) { quizOpen = false }

    // ---- Kids daily limit: counts playing time, stops when today's time is used up.
    var timeUp by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying, features.kidsTimeLimit) {
        if (!features.kidsTimeLimit) return@LaunchedEffect
        if (LuxoKidsTime.exhausted()) { currentController?.pause(); timeUp = true; return@LaunchedEffect }
        while (isPlaying && LuxoKidsTime.active()) {
            delay(15_000L)
            LuxoKidsTime.addSeconds(15)
            if (LuxoKidsTime.exhausted()) {
                currentController?.pause()
                timeUp = true
                break
            }
        }
    }
    if (timeUp) {
        LuxoKidsTimeUpDialog(onExtended = { timeUp = false; currentController?.play() }, onDismiss = { timeUp = false; currentController?.pause() })
    }
}
