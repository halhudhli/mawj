package com.nuvio.app.features.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.padding
import com.nuvio.app.core.ui.luxoPosterFocusEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.NuvioPosterWatchedOverlay
import com.nuvio.app.core.ui.SkeletonPoster
import com.nuvio.app.core.ui.nuvioCardDepth
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.watching.application.WatchingState

internal fun posterGridColumnCountForWidth(screenWidth: Dp): Int =
    when {
        screenWidth >= 1400.dp -> 7
        screenWidth >= 1200.dp -> 6
        screenWidth >= 1000.dp -> 5
        screenWidth >= 840.dp -> 4
        else -> 3
    }

@Composable
internal fun PosterGridRow(
    items: List<MetaPreview>,
    columns: Int,
    modifier: Modifier = Modifier,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items.forEach { item ->
            PosterGridTile(
                item = item,
                cornerRadiusDp = posterCardStyle.cornerRadiusDp,
                hideLabels = posterCardStyle.hideLabelsEnabled,
                modifier = Modifier.weight(1f),
                isWatched = WatchingState.isPosterWatched(
                    watchedKeys = watchedKeys,
                    item = item,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                ),
                onClick = onPosterClick?.let { { it(item) } },
                onLongClick = onPosterLongClick?.let { { it(item) } },
            )
        }
        repeat(columns - items.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun PosterGridSkeletonRow(
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(columns) {
            SkeletonPoster(
                modifier = Modifier.weight(1f),
                cornerRadius = posterCardStyle.cornerRadiusDp.dp,
                showLabels = !posterCardStyle.hideLabelsEnabled,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterGridTile(
    item: MetaPreview,
    cornerRadiusDp: Int,
    hideLabels: Boolean,
    modifier: Modifier = Modifier,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shape = RoundedCornerShape(cornerRadiusDp.dp)
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val imageUrl = item.poster?.takeIf { it.isNotBlank() }
            ?: item.rawPosterUrl?.takeIf { it.isNotBlank() }
            ?: item.banner?.takeIf { it.isNotBlank() }
        val fallbackUrl = listOfNotNull(item.rawPosterUrl, item.banner)
            .firstOrNull { it.isNotBlank() && it != imageUrl }
        var imageFailed by remember(imageUrl, fallbackUrl) { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(item.posterShape.posterGridAspectRatio())
                .luxoPosterFocusEffect(shape = shape, interactionSource = interactionSource, imageUrl = imageUrl),
        ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .nuvioCardDepth(
                    shape = shape,
                    surface = NuvioCardDepthSurface.Posters,
                )
                .posterCardClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    zoomImageUrl = imageUrl,
                    zoomCornerRadius = cornerRadiusDp.dp,
                    interactionSource = interactionSource,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl == null || imageFailed) {
                Text(
                    text = item.name,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (imageUrl != null) {
                val platformContext = coil3.compose.LocalPlatformContext.current
                val imageModel = remember(imageUrl, fallbackUrl, platformContext) {
                    if (fallbackUrl != null) {
                        coil3.request.ImageRequest.Builder(platformContext)
                            .data(imageUrl)
                            .memoryCacheKeyExtras(
                                mapOf(com.nuvio.app.core.poster.CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to fallbackUrl)
                            )
                            .build()
                    } else {
                        imageUrl
                    }
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    onSuccess = { imageFailed = false },
                )
            }
            com.nuvio.app.core.ui.LuxoGlassRatingBadge(
                ratingText = item.imdbRating,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
            NuvioPosterWatchedOverlay(isWatched = isWatched)
        }
        }
        if (!hideLabels) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = item.releaseInfo?.let { formatReleaseDateForDisplay(it) }
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun PosterShape.posterGridAspectRatio(): Float =
    when (this) {
        PosterShape.Poster -> 0.68f
        PosterShape.Square -> 1f
        PosterShape.Landscape -> 1.78f
    }
