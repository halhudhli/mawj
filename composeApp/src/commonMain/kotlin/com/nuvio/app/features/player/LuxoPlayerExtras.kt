package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.details.LuxoCompanionSheet
import kotlinx.coroutines.delay

/**
 * Luxo extras in the player: a sleep timer and «رفيق Luxo». The controls read these callbacks to
 * show their buttons; only one player exists at a time, so a single shared holder is enough.
 */
internal object LuxoPlayerBridge {
    var sleepLabel by mutableStateOf<String?>(null)
    var onSleepTimerClick by mutableStateOf<(() -> Unit)?>(null)
    var onCompanionClick by mutableStateOf<(() -> Unit)?>(null)
}

private const val SLEEP_END_OF_EPISODE = -1L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LuxoPlayerExtrasHost(
    controller: PlayerEngineController?,
    title: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
) {
    val currentController by rememberUpdatedState(controller)
    val currentPosition by rememberUpdatedState(positionMs)
    val currentDuration by rememberUpdatedState(durationMs)

    var sleepSheetOpen by remember { mutableStateOf(false) }
    var companionOpen by remember { mutableStateOf(false) }
    // Absolute deadline in "playback-clock" ms counted down while playing; 0 = off.
    var sleepRemainingMs by remember { mutableLongStateOf(0L) }
    var sleepAtEpisodeEnd by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        LuxoPlayerBridge.onSleepTimerClick = { sleepSheetOpen = true }
        LuxoPlayerBridge.onCompanionClick = {
            currentController?.pause()
            companionOpen = true
        }
        onDispose {
            LuxoPlayerBridge.onSleepTimerClick = null
            LuxoPlayerBridge.onCompanionClick = null
            LuxoPlayerBridge.sleepLabel = null
        }
    }

    // Count down only while the video actually plays, then pause.
    LaunchedEffect(sleepRemainingMs > 0L, isPlaying) {
        while (sleepRemainingMs > 0L && isPlaying) {
            delay(1_000L)
            sleepRemainingMs = (sleepRemainingMs - 1_000L).coerceAtLeast(0L)
            if (sleepRemainingMs == 0L) currentController?.pause()
        }
    }
    LaunchedEffect(sleepAtEpisodeEnd, positionMs, durationMs) {
        if (sleepAtEpisodeEnd && durationMs > 0 && positionMs >= durationMs - 3_000L) {
            sleepAtEpisodeEnd = false
            currentController?.pause()
        }
    }
    LuxoPlayerBridge.sleepLabel = when {
        sleepAtEpisodeEnd -> "نهاية الحلقة"
        sleepRemainingMs > 0L -> "${(sleepRemainingMs + 59_999L) / 60_000L} د"
        else -> null
    }

    if (sleepSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sleepSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("مؤقت النوم", style = MaterialTheme.typography.titleLarge)
                Text(
                    "يوقف التشغيل تلقائياً بعد المدة اللي تختارها (تُحسب وقت التشغيل فقط).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15L, 30L, 45L, 60L, 90L, SLEEP_END_OF_EPISODE).forEach { minutes ->
                        val selected = if (minutes == SLEEP_END_OF_EPISODE) sleepAtEpisodeEnd else
                            sleepRemainingMs > 0L && (sleepRemainingMs + 59_999L) / 60_000L == minutes
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (minutes == SLEEP_END_OF_EPISODE) {
                                    sleepRemainingMs = 0L
                                    sleepAtEpisodeEnd = true
                                } else {
                                    sleepAtEpisodeEnd = false
                                    sleepRemainingMs = minutes * 60_000L
                                }
                                sleepSheetOpen = false
                            },
                            label = { Text(if (minutes == SLEEP_END_OF_EPISODE) "نهاية الحلقة" else "$minutes دقيقة") },
                        )
                    }
                }
                if (sleepRemainingMs > 0L || sleepAtEpisodeEnd) {
                    TextButton(onClick = {
                        sleepRemainingMs = 0L
                        sleepAtEpisodeEnd = false
                        sleepSheetOpen = false
                    }) { Text("إيقاف المؤقت") }
                }
            }
        }
    }

    if (companionOpen) {
        val minute = (currentPosition / 60_000L).toInt()
        val totalMinutes = (currentDuration / 60_000L).toInt()
        val where = buildString {
            append(title)
            if (seasonNumber != null && episodeNumber != null) append(" — الموسم $seasonNumber الحلقة $episodeNumber")
            episodeTitle?.takeIf { it.isNotBlank() }?.let { append(" «").append(it).append("»") }
        }
        val context = buildString {
            append("أنت «رفيق Luxo»، مساعد عربي ودود داخل مشغّل الفيديو. اكتب بالعربية الفصحى السهلة وباختصار، ")
            append("ولا تحرق أي حدث يقع بعد اللحظة الحالية أبداً.\n")
            append("المشاهد يشاهد: ").append(where)
            if (totalMinutes > 0) append("\nوصل إلى الدقيقة $minute من $totalMinutes")
        }
        LuxoCompanionSheet(
            subjectKey = "player:$where",
            subjectTitle = where,
            context = context,
            quickActions = buildList {
                add("وش صار لين الحين؟" to "لخّص ما حدث في هذا العمل حتى اللحظة الحالية تقريباً، بدون أي حرق لما بعدها.")
                if (seasonNumber != null && episodeNumber != null && episodeNumber > 1) {
                    add("ملخص الحلقات اللي قبل" to LuxoCompanionApi.recapPrompt(seasonNumber, episodeNumber - 1))
                }
                add("مين الأبطال؟" to "عرّف بأبطال العمل وشخصياتهم الرئيسية باختصار، بدون حرق.")
                add("معلومة عن العمل" to "اذكر 3 معلومات ممتعة عن إنتاج هذا العمل، بدون حرق.")
            },
            questionLabel = "اسأل عن اللي تشوفه (بدون حرق)",
            onDismiss = { companionOpen = false },
        )
    }
}
