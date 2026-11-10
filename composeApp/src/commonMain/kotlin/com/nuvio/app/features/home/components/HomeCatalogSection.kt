package com.nuvio.app.features.home.components

import androidx.compose.ui.focus.onFocusChanged

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import com.nuvio.app.core.ui.nuvio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState

@Composable
fun HomeCatalogRowSection(
    section: HomeCatalogSection,
    modifier: Modifier = Modifier,
    entries: List<MetaPreview> = section.items,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    sectionPadding: Dp? = null,
    onViewAllClick: (() -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    onPosterFocused: ((MetaPreview) -> Unit)? = null,
) {
    if (sectionPadding != null) {
        HomeCatalogRowSectionContent(
            section = section,
            entries = entries,
            watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            modifier = modifier.fillMaxWidth(),
            sectionPadding = sectionPadding,
            onViewAllClick = onViewAllClick,
            onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick,
            onPosterFocused = onPosterFocused,
        )
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            HomeCatalogRowSectionContent(
                section = section,
                entries = entries,
                watchedKeys = watchedKeys,
                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                modifier = Modifier.fillMaxWidth(),
                sectionPadding = homeSectionHorizontalPaddingForWidth(maxWidth.value),
                onViewAllClick = onViewAllClick,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
            onPosterFocused = onPosterFocused,
            )
        }
    }
}

@Composable
private fun HomeCatalogRowSectionContent(
    section: HomeCatalogSection,
    entries: List<MetaPreview>,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    modifier: Modifier,
    sectionPadding: Dp,
    onViewAllClick: (() -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onPosterFocused: ((MetaPreview) -> Unit)? = null,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    // Mawj «أفضل 10»: popular / trending rows become a ranked row of ten.
    val ranked = com.nuvio.app.features.luxo.rememberLuxoFeatures().top10Rank && luxoIsRankedTitle(section.title) && entries.size >= 5
    val shownEntries = if (ranked) entries.take(10) else entries

    NuvioShelfSection(
        title = section.title,
        entries = shownEntries,
        modifier = modifier,
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        onViewAllClick = onViewAllClick,
        viewAllPillSize = NuvioViewAllPillSize.Compact,
        key = { item -> item.stableKey() },
    ) { item ->
        val rank = if (ranked) shownEntries.indexOf(item) + 1 else 0
        if (rank > 0) androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            LuxoRankNumber(rank)
            HomePosterCard(
                item = item,
                useLandscapeBackdropMode = false,
                isWatched = WatchingState.isPosterWatched(watchedKeys = watchedKeys, item = item, fullyWatchedSeriesKeys = fullyWatchedSeriesKeys),
                onClick = onPosterClick?.let { { it(item) } },
                onLongClick = onPosterLongClick?.let { { it(item) } },
            )
        } else HomePosterCard(
            item = item,
            modifier = Modifier.onFocusChanged { focusState ->
                if (focusState.hasFocus) {
                    onPosterFocused?.invoke(item)
                }
            },
            useLandscapeBackdropMode = posterCardStyle.catalogLandscapeModeEnabled,
            isWatched = WatchingState.isPosterWatched(
                watchedKeys = watchedKeys,
                item = item,
                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            ),
            onClick = onPosterClick?.let { { it(item) } },
            onLongClick = onPosterLongClick?.let { { it(item) } },
        )
    }
}


private fun luxoIsRankedTitle(title: String): Boolean {
    val t = title.lowercase()
    return listOf("popular", "trending", "top 10", "top10", "الأكثر شعبية", "الأكثر مشاهدة", "الرائج", "رائج", "شائع", "أفضل 10").any { it in t }
}

/** The big outlined rank number that sits behind and beside the poster, like a «Top 10» row. */
@Composable
private fun LuxoRankNumber(rank: Int) {
    val color = androidx.compose.material3.MaterialTheme.nuvio.colors.textPrimary
    androidx.compose.foundation.layout.Box(Modifier.offset(x = Dp(if (rank >= 10) 0f else 6f))) {
        androidx.compose.material3.Text(
            rank.toString(),
            fontSize = androidx.compose.ui.unit.TextUnit(96f, androidx.compose.ui.unit.TextUnitType.Sp),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            color = color.copy(alpha = 0.9f),
            style = androidx.compose.ui.text.TextStyle(
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f),
                lineHeight = androidx.compose.ui.unit.TextUnit(88f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
            maxLines = 1,
        )
    }
}
