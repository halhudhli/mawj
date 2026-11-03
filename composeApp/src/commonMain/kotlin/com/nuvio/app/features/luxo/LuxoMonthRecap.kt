package com.nuvio.app.features.luxo

import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressRepository

/** Builds the context for «شهرك في Luxo»: what was watched in the last 30 days. */
internal object LuxoMonthRecap {
    private const val WINDOW_MS = 30L * 24 * 60 * 60 * 1000

    fun context(): String {
        val now = WatchProgressClock.nowEpochMs()
        val since = now - WINDOW_MS
        val watched = WatchedRepository.uiState.value.items.filter { it.markedAtEpochMs >= since }
        val progress = WatchProgressRepository.uiState.value.entries.filter { it.lastUpdatedEpochMs >= since }

        val movies = watched.filter { it.type == "movie" }.map { it.name }.distinct()
        val episodesByShow = watched.filter { it.season != null }.groupBy { it.name }.mapValues { it.value.size }
        val inProgress = progress.map { it.title }.distinct()
        val minutes = progress.sumOf { it.lastPositionMs } / 60_000L

        return buildString {
            append("أنت «رفيق موج». اكتب للمشاهد ملخصاً شهرياً ممتعاً بالعربية لمشاهداته خلال آخر 30 يوماً، بأسلوب مثل Spotify Wrapped: ")
            append("عناوين قصيرة، أرقام، ملاحظة عن ذوقه، واقتراحان لما يشاهده بعدها. لا تختلق أعمالاً لم تُذكر.\n")
            append("الأفلام المكتملة (${movies.size}): ").append(movies.joinToString("، ").ifBlank { "لا يوجد" })
            append("\nحلقات المسلسلات المكتملة: ")
            append(episodesByShow.entries.joinToString("، ") { "${it.key} (${it.value} حلقة)" }.ifBlank { "لا يوجد" })
            append("\nأعمال بدأها ولم يكملها: ").append(inProgress.joinToString("، ").ifBlank { "لا يوجد" })
            append("\nتقريباً $minutes دقيقة من التقدم المسجّل.")
        }
    }
}
