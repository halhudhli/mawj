package com.nuvio.app.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape

@Composable
fun HomePosterCard(
    item: MetaPreview,
    modifier: Modifier = Modifier,
    useLandscapeBackdropMode: Boolean = false,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    showLandscapeOverlay: Boolean = true,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val isLandscapeMode = useLandscapeBackdropMode || posterCardStyle.catalogLandscapeModeEnabled
    val effectiveLandscapePoster = if (posterCardStyle.alwaysShowLandscapeClearlogo) null else item.landscapePoster
    // Custom poster first (e.g. a ratings poster), then the addon's own poster, then the backdrop,
    // so a card is never left empty.
    val portraitUrl = item.poster?.takeIf { it.isNotBlank() }
        ?: item.rawPosterUrl?.takeIf { it.isNotBlank() }
        ?: item.banner?.takeIf { it.isNotBlank() }
    val portraitFallback = item.rawPosterUrl?.takeIf { it.isNotBlank() && it != portraitUrl }
        ?: item.banner?.takeIf { it.isNotBlank() && it != portraitUrl }
    val imageUrl = if (isLandscapeMode) {
        effectiveLandscapePoster ?: item.banner?.takeIf { it.isNotBlank() } ?: portraitUrl
    } else {
        portraitUrl
    }
    val fallbackImageUrl = if (isLandscapeMode) {
        listOfNotNull(item.banner, item.rawPosterUrl, item.poster)
            .firstOrNull { it.isNotBlank() && it != imageUrl }
    } else {
        portraitFallback
    }

    // Mawj: when a title has several posters, they take turns softly (Arabic first, then others).
    val rotating = !isLandscapeMode && com.nuvio.app.features.luxo.rememberLuxoFeatures().posterRotate
    val variants by androidx.compose.runtime.produceState(
        initialValue = com.nuvio.app.features.luxo.MawjPosterVariants.cached(item.id, item.type).orEmpty(),
        key1 = item.id, key2 = rotating,
    ) {
        if (rotating && value.isEmpty()) value = com.nuvio.app.features.luxo.MawjPosterVariants.load(item.id, item.type)
    }
    var turn by androidx.compose.runtime.remember(item.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    if (rotating && variants.size > 1) {
        androidx.compose.runtime.LaunchedEffect(item.id, variants.size) {
            kotlinx.coroutines.delay(2_500L + (item.id.hashCode().toLong() and 0x1FFF) % 5_000L)
            while (true) {
                kotlinx.coroutines.delay(8_000L)
                turn = (turn + 1) % variants.size
            }
        }
    }
    val shownUrl = if (rotating && variants.isNotEmpty()) variants[turn % variants.size] else imageUrl

    NuvioPosterCard(
        title = item.name,
        imageUrl = shownUrl,
        modifier = modifier,
        fallbackImageUrl = fallbackImageUrl,
        shape = if (isLandscapeMode) NuvioPosterShape.Landscape else item.posterShape.toNuvioPosterShape(),
        detailLine = if (isLandscapeMode || posterCardStyle.hideLabelsEnabled) null else item.releaseInfo?.let { formatReleaseDateForDisplay(it) },
        showTitleBelow = !posterCardStyle.hideLabelsEnabled,
        bottomLeftLogoUrl = if (isLandscapeMode && showLandscapeOverlay && effectiveLandscapePoster.isNullOrBlank()) item.logo else null,
        bottomLeftText = if (isLandscapeMode && showLandscapeOverlay && effectiveLandscapePoster.isNullOrBlank() && item.logo.isNullOrBlank() && !posterCardStyle.hideLabelsEnabled) item.name else null,
        isWatched = isWatched,
        onClick = onClick,
        onLongClick = onLongClick,
        ratingText = item.imdbRating,
    )
}

private fun PosterShape.toNuvioPosterShape(): NuvioPosterShape =
    when (this) {
        PosterShape.Poster -> NuvioPosterShape.Poster
        PosterShape.Square -> NuvioPosterShape.Square
        PosterShape.Landscape -> NuvioPosterShape.Landscape
    }
