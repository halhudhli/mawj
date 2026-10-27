package com.nuvio.app

import androidx.compose.runtime.collectAsState
import com.nuvio.app.core.ui.NativeTabBridge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.FloatingNavigationBar
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.LocalNuvioNavBarScrollState
import com.nuvio.app.core.ui.NuvioClassicNavigationBar
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.LocalNuvioTabletNavLayout
import com.nuvio.app.core.ui.floatingNavigationBarPadding
import com.nuvio.app.features.settings.NavBarPosition
import com.nuvio.app.core.ui.rememberNuvioNavBarScrollState
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileSwitcherTab
import com.nuvio.app.features.settings.NavBarStyle
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.kyant.backdrop.Backdrop
import com.nuvio.app.core.ui.glass.nuvioBackdropSource
import com.nuvio.app.core.ui.glass.rememberNuvioBackdrop
import com.nuvio.app.core.ui.glass.LiquidGlassEffectDefaults
import com.nuvio.app.core.ui.glass.LiquidGlassEffectSettings
import com.nuvio.app.core.ui.glass.LiquidGlassStyle
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_home
import nuvio.composeapp.generated.resources.compose_nav_library
import nuvio.composeapp.generated.resources.compose_nav_live_tv
import nuvio.composeapp.generated.resources.compose_nav_profile
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.sidebar_library
import nuvio.composeapp.generated.resources.sidebar_search
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Composable
internal fun MainTabsDestination(
    selectedTab: AppScreenTab,
    initialHomeReady: Boolean,
    rootRouteActive: Boolean,
    useTabletFloatingTabBar: Boolean,
    useNativeNavigation: Boolean,
    useNativeTabBar: Boolean,
    liquidGlassNativeTabBarSupported: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    showLiveTvInNavigation: Boolean,
    requests: AppTabRequests,
    state: AppTabState,
    actions: (isTabletLayout: Boolean) -> AppTabActions,
    onBack: () -> Unit,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onBack)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletLayout = useTabletFloatingTabBar || maxWidth >= 768.dp
        val nativeActiveTab by NativeTabBridge.activeTab.collectAsState()
        val highlightedTab = if (useNativeNavigation) nativeActiveTab.toAppScreenTab() else selectedTab
        val tabActions = remember(actions, isTabletLayout) { actions(isTabletLayout) }
        val useNativeBottomTabs = if (useNativeNavigation) {
            useNativeTabBar
        } else {
            liquidGlassNativeTabBarSupported && liquidGlassNativeTabBarEnabled && initialHomeReady
        }
        val tabsRouteActive = rootRouteActive
        val navBarScrollState = rememberNuvioNavBarScrollState()
        val navBarStyleSetting by remember { ThemeSettingsRepository.navBarStyle }.collectAsStateWithLifecycle()
        val navBarGlowEnabled by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
        val navBarPosition by ThemeSettingsRepository.navBarPosition.collectAsStateWithLifecycle()
        val navBarGlass by ThemeSettingsRepository.navBarGlass.collectAsStateWithLifecycle()
        val navBarDefaultIconColorEnabled by ThemeSettingsRepository.navBarDefaultIconColorEnabled.collectAsStateWithLifecycle()
        val navBarDefaultIconColor by ThemeSettingsRepository.navBarDefaultIconColor.collectAsStateWithLifecycle()
        val navBarDefaultIconColorOverride = if (navBarDefaultIconColorEnabled) {
            Color(navBarDefaultIconColor or 0xFF000000.toInt())
        } else {
            null
        }
        val luxoNav = luxoNavSettingsState()
        // Luxo: a side rail on the reading-start edge (RTL puts it on the right).
        val useSideRail = tabsRouteActive && !useNativeBottomTabs && when (luxoNav.placementValue) {
            com.nuvio.app.features.luxo.LuxoNavPlacement.SIDE -> true
            com.nuvio.app.features.luxo.LuxoNavPlacement.AUTO -> maxWidth > maxHeight && maxWidth >= 700.dp
            com.nuvio.app.features.luxo.LuxoNavPlacement.BAR -> false
        }
        val navHide = remember { com.nuvio.app.features.luxo.LuxoNavHideState() }
        androidx.compose.runtime.LaunchedEffect(selectedTab) { navHide.hidden = false }
        val navHidden = luxoNav.hideOnScroll && navHide.hidden
        val navHideProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (navHidden) 1f else 0f,
            label = "luxo_nav_hide",
        )
        var tonightOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        val luxoHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        val luxoHapticsOn = com.nuvio.app.features.luxo.rememberLuxoFeatures().haptics
        val onTabLongPressed: (AppScreenTab) -> Unit = { tab ->
            if (luxoHapticsOn) luxoHaptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            when (tab) {
                AppScreenTab.Home -> tonightOpen = true
                AppScreenTab.Search -> {
                    onTabSelected(AppScreenTab.Search)
                    onTabSelected(AppScreenTab.Search)
                }
                AppScreenTab.Library -> tabActions.onDownloadsClick()
                else -> onTabSelected(tab)
            }
        }
        val floatingBarOnTop = navBarStyleSetting != NavBarStyle.CLASSIC && navBarPosition == NavBarPosition.TOP
        // The backdrop source is only consumed by the custom floating bar, not native or CLASSIC navigation.
        val floatingBarVisible = tabsRouteActive && !useNativeBottomTabs && navBarStyleSetting != NavBarStyle.CLASSIC && !useSideRail
        val navBarBackdrop = if (floatingBarVisible) rememberNuvioBackdrop() else null
        val floatingNavigationItems = mainFloatingNavigationItems(
            highlightedTab = highlightedTab,
            showLiveTv = showLiveTvInNavigation,
            onTabSelected = onTabSelected,
            onProfileSelected = onProfileSelected,
            onAddProfileRequested = onAddProfileRequested,
            backdrop = navBarBackdrop,
            defaultIconColorOverride = navBarDefaultIconColorOverride,
            onTabLongPressed = onTabLongPressed,
        )

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (initialHomeReady) 1f else 0f),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (tabsRouteActive && !isTabletLayout && !useNativeBottomTabs && navBarStyleSetting == NavBarStyle.CLASSIC && !useSideRail) {
                    MainClassicNavigationBar(
                        modifier = Modifier.graphicsLayer {
                            translationY = size.height * navHideProgress
                        },
                        highlightedTab = highlightedTab,
                        showLiveTv = showLiveTvInNavigation,
                        onTabSelected = onTabSelected,
                        onProfileSelected = onProfileSelected,
                        onAddProfileRequested = onAddProfileRequested,
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalNuvioBottomNavigationOverlayPadding provides when {
                        tabsRouteActive && useNativeBottomTabs -> 49.dp
                        useSideRail -> 0.dp
                        // A top pill floats over the content like the tablet CLASSIC bar, so
                        // nothing needs to be kept clear at the bottom.
                        tabsRouteActive && navBarStyleSetting != NavBarStyle.CLASSIC && !floatingBarOnTop -> 72.dp
                        else -> 0.dp
                    },
                    LocalNuvioNavBarScrollState provides navBarScrollState,
                    LocalNuvioTabletNavLayout provides isTabletLayout,
                ) {
                    AppTabHost(
                        selectedTab = selectedTab,
                        requests = requests,
                        state = state,
                        actions = tabActions,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (floatingBarVisible) {
                                    navBarBackdrop?.let { Modifier.nuvioBackdropSource(it) } ?: Modifier
                                } else {
                                    Modifier
                                },
                            )
                            .then(if (tabsRouteActive && navBarStyleSetting == NavBarStyle.ADAPTIVE) Modifier.nestedScroll(navBarScrollState.nestedScrollConnection) else Modifier)
                            .then(if (luxoNav.hideOnScroll) Modifier.nestedScroll(navHide.connection) else Modifier)
                            .padding(innerPadding)
                            .padding(start = if (useSideRail) com.nuvio.app.features.luxo.LuxoRailInset else 0.dp),
                    )
                }

                // CLASSIC keeps the previous per-form-factor chrome: a top rail on
                // tablet/landscape and the solid bottom bar on phones. Every other
                // style now uses the floating pill at the bottom on all sizes.
                if (isTabletLayout && !useNativeBottomTabs && navBarStyleSetting == NavBarStyle.CLASSIC && !useSideRail) {
                    TabletFloatingTopBar(
                        selectedTab = highlightedTab,
                        showLiveTv = showLiveTvInNavigation,
                        onTabSelected = onTabSelected,
                        onProfileSelected = onProfileSelected,
                        onAddProfileRequested = onAddProfileRequested,
                    )
                }

                if (floatingBarVisible) {
                    when (navBarStyleSetting) {
                        NavBarStyle.EXPANDED -> navBarScrollState.expand()
                        NavBarStyle.COMPACT -> navBarScrollState.collapse()
                        else -> {}
                    }
                    if (com.nuvio.app.features.luxo.rememberLuxoFeatures().luxoDock) {
                        com.nuvio.app.features.luxo.LuxoDockBar(
                            items = floatingNavigationItems,
                            hideProgress = navHideProgress,
                            onTop = floatingBarOnTop,
                            contentPadding = if (floatingBarOnTop) {
                                PaddingValues(
                                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                                    bottom = 8.dp,
                                )
                            } else {
                                floatingNavigationBarPadding()
                            },
                            modifier = Modifier.align(if (floatingBarOnTop) Alignment.TopCenter else Alignment.BottomCenter),
                        )
                    } else MainFloatingNavigationBar(
                        modifier = Modifier
                            .align(if (floatingBarOnTop) Alignment.TopCenter else Alignment.BottomCenter)
                            .graphicsLayer {
                                translationY = (if (floatingBarOnTop) -1f else 1f) * 160.dp.toPx() * navHideProgress
                                alpha = 1f - navHideProgress
                            },
                        scrollState = navBarScrollState,
                        backdrop = navBarBackdrop,
                        contentPadding = if (floatingBarOnTop) {
                            PaddingValues(
                                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                                bottom = 8.dp,
                            )
                        } else {
                            floatingNavigationBarPadding()
                        },
                        items = floatingNavigationItems,
                        glowEnabled = navBarGlowEnabled,
                        inlineLabels = isTabletLayout,
                        glassStyle = navBarGlass,
                        defaultIconColorOverride = navBarDefaultIconColorOverride,
                    )
                }

                if (useSideRail) {
                    com.nuvio.app.features.luxo.LuxoSideRail(
                        items = floatingNavigationItems,
                        expanded = luxoNav.railExpanded,
                        onExpandedChange = { open ->
                            com.nuvio.app.features.luxo.LuxoNavSettingsRepository.update { it.copy(railExpanded = open) }
                        },
                        hidden = navHidden,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
            }
        }
        if (tonightOpen) {
            com.nuvio.app.features.luxo.LuxoTonightSheet(
                onDismiss = { tonightOpen = false },
                onOpen = { picked ->
                    tonightOpen = false
                    tabActions.onPosterClick?.invoke(picked)
                },
            )
        }
    }
}

/**
 * Keep the high-frequency glass-effect draft flows subscribed in the navigation-bar leaf.
 * Dragging effect sliders then updates this bar without invalidating MainTabsDestination and its tab host.
 */
@Composable
private fun MainFloatingNavigationBar(
    modifier: Modifier,
    scrollState: com.nuvio.app.core.ui.NuvioNavBarScrollState?,
    backdrop: Backdrop?,
    contentPadding: PaddingValues,
    items: List<FloatingNavigationItem>,
    glowEnabled: Boolean,
    inlineLabels: Boolean,
    glassStyle: LiquidGlassStyle,
    defaultIconColorOverride: Color?,
) {
    val liquidGlassBarEffects by ThemeSettingsRepository.navBarLiquidGlassBarEffects.collectAsStateWithLifecycle()
    val liquidGlassSelectionEffects by ThemeSettingsRepository.navBarLiquidGlassSelectionEffects.collectAsStateWithLifecycle()

    FloatingNavigationBar(
        items = items,
        modifier = modifier,
        scrollState = scrollState,
        backdrop = backdrop,
        glassStyle = glassStyle,
        contentPadding = contentPadding,
        glowEnabled = glowEnabled,
        liquidGlassBarEffects = liquidGlassBarEffects,
        liquidGlassSelectionEffects = liquidGlassSelectionEffects,
        inlineLabels = inlineLabels,
        defaultIconColorOverride = defaultIconColorOverride,
    )
}

@Composable
internal fun mainFloatingNavigationItems(
    highlightedTab: AppScreenTab,
    showLiveTv: Boolean,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
    backdrop: Backdrop?,
    defaultIconColorOverride: Color? = null,
    onTabLongPressed: ((AppScreenTab) -> Unit)? = null,
): List<FloatingNavigationItem> {
    val luxoNav = luxoNavSettingsState()
    val todayCount = com.nuvio.app.features.luxo.rememberLuxoTodayEpisodeCount(luxoNav.badges)
    val longPress: (AppScreenTab) -> (() -> Unit)? = { tab ->
        if (luxoNav.quickActions && onTabLongPressed != null) ({ onTabLongPressed(tab) }) else null
    }
    val homeLabel = stringResource(Res.string.compose_nav_home)
    val searchLabel = stringResource(Res.string.compose_nav_search)
    val libraryLabel = stringResource(Res.string.compose_nav_library)
    val liveLabel = stringResource(Res.string.compose_nav_live_tv)
    val profileLabel = stringResource(Res.string.compose_nav_profile)
    val moviesLabel = com.nuvio.app.features.luxo.lx("أفلام", "Movies")
    val seriesLabel = com.nuvio.app.features.luxo.lx("مسلسلات", "Series")
    val listsLabel = com.nuvio.app.features.luxo.lx("قوائمي", "Lists")
    val animeLabel = com.nuvio.app.features.luxo.lx("أنمي", "Anime")
    val animeOn = com.nuvio.app.features.luxo.rememberLuxoFeatures().animeSection
    val ids = com.nuvio.app.features.luxo.LuxoNavTabIds
    val tabs = buildList {
        add(
            FloatingNavigationItem(
                selected = highlightedTab == AppScreenTab.Home,
                onClick = { onTabSelected(AppScreenTab.Home) },
                icon = Icons.Filled.Home,
                label = homeLabel,
                id = ids.HOME,
                onLongClick = longPress(AppScreenTab.Home),
            ),
        )
        if (luxoNav.showMovies) {
            add(
                FloatingNavigationItem(
                    selected = highlightedTab == AppScreenTab.Movies,
                    onClick = { onTabSelected(AppScreenTab.Movies) },
                    icon = Icons.Filled.Movie,
                    label = moviesLabel,
                    id = ids.MOVIES,
                    onLongClick = longPress(AppScreenTab.Movies),
                ),
            )
        }
        if (luxoNav.showSeries) {
            add(
                FloatingNavigationItem(
                    selected = highlightedTab == AppScreenTab.Series,
                    onClick = { onTabSelected(AppScreenTab.Series) },
                    icon = Icons.Filled.VideoLibrary,
                    label = seriesLabel,
                    id = ids.SERIES,
                    onLongClick = longPress(AppScreenTab.Series),
                ),
            )
        }
        if (animeOn) {
            add(
                FloatingNavigationItem(
                    selected = highlightedTab == AppScreenTab.Anime,
                    onClick = { onTabSelected(AppScreenTab.Anime) },
                    icon = Icons.Filled.Animation,
                    label = animeLabel,
                    id = ids.ANIME,
                    onLongClick = longPress(AppScreenTab.Anime),
                ),
            )
        }
        if (luxoNav.showLists) {
            add(
                FloatingNavigationItem(
                    selected = highlightedTab == AppScreenTab.Lists,
                    onClick = { onTabSelected(AppScreenTab.Lists) },
                    icon = Icons.Filled.Bookmarks,
                    label = listsLabel,
                    id = ids.LISTS,
                    onLongClick = longPress(AppScreenTab.Lists),
                ),
            )
        }
        add(
            FloatingNavigationItem(
                selected = highlightedTab == AppScreenTab.Search,
                onClick = { onTabSelected(AppScreenTab.Search) },
                drawable = Res.drawable.sidebar_search,
                label = searchLabel,
                id = ids.SEARCH,
                onLongClick = longPress(AppScreenTab.Search),
            ),
        )
        add(
            FloatingNavigationItem(
                selected = highlightedTab == AppScreenTab.Library,
                onClick = { onTabSelected(AppScreenTab.Library) },
                drawable = Res.drawable.sidebar_library,
                label = libraryLabel,
                id = ids.LIBRARY,
                onLongClick = longPress(AppScreenTab.Library),
                badge = todayCount,
            ),
        )
        if (showLiveTv) {
            add(
                FloatingNavigationItem(
                    selected = highlightedTab == AppScreenTab.LiveTv,
                    onClick = { onTabSelected(AppScreenTab.LiveTv) },
                    icon = Icons.Filled.Tv,
                    label = liveLabel,
                    id = ids.LIVE_TV,
                    onLongClick = longPress(AppScreenTab.LiveTv),
                ),
            )
        }
    }.sortedBy { luxoNav.rank(it.id) }
    return tabs + FloatingNavigationItem(
        selected = highlightedTab == AppScreenTab.Settings,
        onClick = { onTabSelected(AppScreenTab.Settings) },
        label = profileLabel,
        id = "profile",
        content = { onClick, iconColorOverride ->
            ProfileSwitcherTab(
                selected = highlightedTab == AppScreenTab.Settings,
                onClick = onClick,
                onProfileSelected = onProfileSelected,
                onAddProfileRequested = onAddProfileRequested,
                backdrop = backdrop,
                popupBelowAnchor = false,
                defaultIconColorOverride = iconColorOverride ?: defaultIconColorOverride,
            )
        },
    )
}

@Composable
internal fun MainClassicNavigationBar(
    highlightedTab: AppScreenTab,
    showLiveTv: Boolean,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val luxoNav = luxoNavSettingsState()
    val ids = com.nuvio.app.features.luxo.LuxoNavTabIds
    val homeLabel = stringResource(Res.string.compose_nav_home)
    val searchLabel = stringResource(Res.string.compose_nav_search)
    val libraryLabel = stringResource(Res.string.compose_nav_library)
    val liveLabel = stringResource(Res.string.compose_nav_live_tv)
    val moviesLabel = com.nuvio.app.features.luxo.lx("أفلام", "Movies")
    val seriesLabel = com.nuvio.app.features.luxo.lx("مسلسلات", "Series")
    val listsLabel = com.nuvio.app.features.luxo.lx("قوائمي", "Lists")
    val animeLabel = com.nuvio.app.features.luxo.lx("أنمي", "Anime")
    val animeOn = com.nuvio.app.features.luxo.rememberLuxoFeatures().animeSection
    val entries = buildList<Pair<String, @Composable com.nuvio.app.core.ui.NuvioNavigationBarScope.() -> Unit>> {
        add(luxoNavEntry(ids.HOME) { NavItem(highlightedTab == AppScreenTab.Home, { onTabSelected(AppScreenTab.Home) }, Icons.Filled.Home, homeLabel) })
        if (luxoNav.showMovies) add(luxoNavEntry(ids.MOVIES) { NavItem(highlightedTab == AppScreenTab.Movies, { onTabSelected(AppScreenTab.Movies) }, Icons.Filled.Movie, moviesLabel) })
        if (luxoNav.showSeries) add(luxoNavEntry(ids.SERIES) { NavItem(highlightedTab == AppScreenTab.Series, { onTabSelected(AppScreenTab.Series) }, Icons.Filled.VideoLibrary, seriesLabel) })
        if (animeOn) add(luxoNavEntry(ids.ANIME) { NavItem(highlightedTab == AppScreenTab.Anime, { onTabSelected(AppScreenTab.Anime) }, Icons.Filled.Animation, animeLabel) })
        if (luxoNav.showLists) add(luxoNavEntry(ids.LISTS) { NavItem(highlightedTab == AppScreenTab.Lists, { onTabSelected(AppScreenTab.Lists) }, Icons.Filled.Bookmarks, listsLabel) })
        add(luxoNavEntry(ids.SEARCH) { NavItem(highlightedTab == AppScreenTab.Search, { onTabSelected(AppScreenTab.Search) }, Res.drawable.sidebar_search, searchLabel) })
        add(luxoNavEntry(ids.LIBRARY) { NavItem(highlightedTab == AppScreenTab.Library, { onTabSelected(AppScreenTab.Library) }, Res.drawable.sidebar_library, libraryLabel) })
        if (showLiveTv) add(luxoNavEntry(ids.LIVE_TV) { NavItem(highlightedTab == AppScreenTab.LiveTv, { onTabSelected(AppScreenTab.LiveTv) }, Icons.Filled.Tv, liveLabel) })
    }.sortedBy { luxoNav.rank(it.first) }
    NuvioClassicNavigationBar(modifier) {
        entries.forEach { (_, item) -> item() }
        NavItem(
            selected = highlightedTab == AppScreenTab.Settings,
            onClick = { onTabSelected(AppScreenTab.Settings) },
        ) {
            ProfileSwitcherTab(
                selected = highlightedTab == AppScreenTab.Settings,
                onClick = { onTabSelected(AppScreenTab.Settings) },
                onProfileSelected = onProfileSelected,
                onAddProfileRequested = onAddProfileRequested,
            )
        }
    }
}

@Composable
internal fun BoxScope.SettingsRouteNavigationBar(
    isTabletLayout: Boolean,
    showLiveTv: Boolean,
    backdrop: Backdrop?,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
    onBottomOverlayChanged: (Dp) -> Unit,
    isWideLandscape: Boolean = false,
    onStartOverlayChanged: (Dp) -> Unit = {},
) {
    val style by remember { ThemeSettingsRepository.navBarStyle }.collectAsStateWithLifecycle()
    val glowEnabled by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
    val position by ThemeSettingsRepository.navBarPosition.collectAsStateWithLifecycle()
    val glass by ThemeSettingsRepository.navBarGlass.collectAsStateWithLifecycle()
    val liquidGlassBarEffects by ThemeSettingsRepository.navBarLiquidGlassBarEffects.collectAsStateWithLifecycle()
    val liquidGlassSelectionEffects by ThemeSettingsRepository.navBarLiquidGlassSelectionEffects.collectAsStateWithLifecycle()
    val defaultIconColorEnabled by ThemeSettingsRepository.navBarDefaultIconColorEnabled.collectAsStateWithLifecycle()
    val defaultIconColor by ThemeSettingsRepository.navBarDefaultIconColor.collectAsStateWithLifecycle()
    val defaultIconColorOverride = if (defaultIconColorEnabled) {
        Color(defaultIconColor or 0xFF000000.toInt())
    } else {
        null
    }
    val density = LocalDensity.current
    val highlightedTab = AppScreenTab.Settings
    val luxoNav = luxoNavSettingsState()
    val useSideRail = when (luxoNav.placementValue) {
        com.nuvio.app.features.luxo.LuxoNavPlacement.SIDE -> true
        com.nuvio.app.features.luxo.LuxoNavPlacement.AUTO -> isWideLandscape
        com.nuvio.app.features.luxo.LuxoNavPlacement.BAR -> false
    }
    SideEffect { onStartOverlayChanged(if (useSideRail) com.nuvio.app.features.luxo.LuxoRailInset else 0.dp) }

    when {
        useSideRail -> {
            SideEffect { onBottomOverlayChanged(0.dp) }
            com.nuvio.app.features.luxo.LuxoSideRail(
                items = mainFloatingNavigationItems(
                    highlightedTab = highlightedTab,
                    showLiveTv = showLiveTv,
                    onTabSelected = onTabSelected,
                    onProfileSelected = onProfileSelected,
                    onAddProfileRequested = onAddProfileRequested,
                    backdrop = backdrop,
                    defaultIconColorOverride = defaultIconColorOverride,
                ),
                expanded = luxoNav.railExpanded,
                onExpandedChange = { open ->
                    com.nuvio.app.features.luxo.LuxoNavSettingsRepository.update { it.copy(railExpanded = open) }
                },
                hidden = false,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        style == NavBarStyle.CLASSIC && isTabletLayout -> {
            SideEffect { onBottomOverlayChanged(0.dp) }
            TabletFloatingTopBar(
                selectedTab = highlightedTab,
                showLiveTv = showLiveTv,
                onTabSelected = onTabSelected,
                onProfileSelected = onProfileSelected,
                onAddProfileRequested = onAddProfileRequested,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        style == NavBarStyle.CLASSIC -> {
            Box(
                Modifier.align(Alignment.BottomCenter).onSizeChanged {
                    onBottomOverlayChanged(with(density) { it.height.toDp() })
                },
            ) {
                MainClassicNavigationBar(
                    highlightedTab = highlightedTab,
                    showLiveTv = showLiveTv,
                    onTabSelected = onTabSelected,
                    onProfileSelected = onProfileSelected,
                    onAddProfileRequested = onAddProfileRequested,
                )
            }
        }
        com.nuvio.app.features.luxo.rememberLuxoFeatures().luxoDock -> {
            val onTop = position == NavBarPosition.TOP
            SideEffect { onBottomOverlayChanged(if (onTop) 0.dp else 72.dp) }
            com.nuvio.app.features.luxo.LuxoDockBar(
                items = mainFloatingNavigationItems(
                    highlightedTab = highlightedTab,
                    showLiveTv = showLiveTv,
                    onTabSelected = onTabSelected,
                    onProfileSelected = onProfileSelected,
                    onAddProfileRequested = onAddProfileRequested,
                    backdrop = backdrop,
                    defaultIconColorOverride = defaultIconColorOverride,
                ),
                hideProgress = 0f,
                onTop = onTop,
                contentPadding = if (onTop) {
                    PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                        bottom = 8.dp,
                    )
                } else {
                    floatingNavigationBarPadding()
                },
                modifier = Modifier.align(if (onTop) Alignment.TopCenter else Alignment.BottomCenter),
            )
        }
        else -> {
            val onTop = position == NavBarPosition.TOP
            SideEffect { onBottomOverlayChanged(if (onTop) 0.dp else 72.dp) }
            FloatingNavigationBar(
                items = mainFloatingNavigationItems(
                    highlightedTab = highlightedTab,
                    showLiveTv = showLiveTv,
                    onTabSelected = onTabSelected,
                    onProfileSelected = onProfileSelected,
                    onAddProfileRequested = onAddProfileRequested,
                    backdrop = backdrop,
                    defaultIconColorOverride = defaultIconColorOverride,
                ),
                modifier = Modifier.align(if (onTop) Alignment.TopCenter else Alignment.BottomCenter),
                scrollState = if (style == NavBarStyle.COMPACT) {
                    remember { com.nuvio.app.core.ui.NuvioNavBarScrollState().apply { collapse() } }
                } else {
                    null
                },
                backdrop = backdrop,
                contentPadding = if (onTop) {
                    PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                        bottom = 8.dp,
                    )
                } else {
                    floatingNavigationBarPadding()
                },
                glowEnabled = glowEnabled,
                inlineLabels = isTabletLayout,
                glassStyle = glass,
                liquidGlassBarEffects = liquidGlassBarEffects,
                liquidGlassSelectionEffects = liquidGlassSelectionEffects,
                defaultIconColorOverride = defaultIconColorOverride,
            )
        }
    }
}

@Composable
private fun luxoNavSettingsState(): com.nuvio.app.features.luxo.LuxoNavSettings {
    val state by androidx.compose.runtime.remember {
        com.nuvio.app.features.luxo.LuxoNavSettingsRepository.ensureLoaded()
        com.nuvio.app.features.luxo.LuxoNavSettingsRepository.state
    }.collectAsStateWithLifecycle()
    return state
}

private fun luxoNavEntry(
    id: String,
    body: @Composable com.nuvio.app.core.ui.NuvioNavigationBarScope.() -> Unit,
): Pair<String, @Composable com.nuvio.app.core.ui.NuvioNavigationBarScope.() -> Unit> = id to body
