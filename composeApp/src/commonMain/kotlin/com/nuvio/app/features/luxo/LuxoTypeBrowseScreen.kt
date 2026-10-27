package com.nuvio.app.features.luxo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Content types used by Stremio-style addons such as Cinemata. */
internal object LuxoContentType {
    const val MOVIE = "movie"
    const val SERIES = "series"
}

/**
 * A Cinemata-style section tab (Movies / Series): every home catalog row of one content type,
 * reusing the rows the home screen already loaded, so it costs no extra network requests.
 */
@Composable
internal fun LuxoTypeBrowseScreen(
    contentType: String,
    title: String,
    modifier: Modifier = Modifier,
    scrollToTopRequests: Flow<Unit> = emptyFlow(),
    onCatalogClick: ((HomeCatalogSection) -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    val homeUiState by HomeRepository.uiState.collectAsStateWithLifecycle()
    val sections = remember(homeUiState.sections, contentType) {
        homeUiState.sections.filter { section ->
            section.items.isNotEmpty() &&
                section.target.contentType.equals(contentType, ignoreCase = true)
        }
    }
    val smartSections by produceState(initialValue = emptyList<HomeCatalogSection>(), contentType) {
        value = runCatching { LuxoSmartCollections.load(contentType) }.getOrDefault(emptyList())
    }
    val visibleSmartSections = remember(smartSections) {
        smartSections.filter { it.items.isNotEmpty() }
    }
    val listState = rememberLazyListState()
    ScreenActivityEffect(scrollToTopRequests) { active ->
        if (!active) return@ScreenActivityEffect
        scrollToTopRequests.collect { listState.animateScrollToItem(0) }
    }

    NuvioScreen(
        modifier = modifier.fillMaxSize(),
        horizontalPadding = 0.dp,
        listState = listState,
        autoHidesNativeTabBar = true,
    ) {
        item(key = "header") {
            NuvioScreenHeader(
                title = title,
                includeStatusBarPadding = false,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "luxo_tonight") {
            LuxoTonightCard(
                onOpen = { item -> onPosterClick?.invoke(item) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(visibleSmartSections, key = { it.key }) { section ->
            HomeCatalogRowSection(
                section = section,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
            )
        }
        when {
            sections.isNotEmpty() -> items(sections, key = { it.key }) { section ->
                HomeCatalogRowSection(
                    section = section,
                    onViewAllClick = onCatalogClick?.let { click -> { click(section) } },
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                )
            }
            homeUiState.isLoading -> item(key = "loading") {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            visibleSmartSections.isNotEmpty() -> Unit
            else -> item(key = "empty") {
                Text(
                    text = lx(
                        "ما فيه أقسام هنا للحين. أضف إضافة مثل Cinemata من الإعدادات ← الإضافات، وفعّل أقسامها في الشاشة الرئيسية.",
                        "Nothing here yet. Add an addon such as Cinemata in Settings → Addons and enable its catalogs on the home screen.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                )
            }
        }
    }
}
