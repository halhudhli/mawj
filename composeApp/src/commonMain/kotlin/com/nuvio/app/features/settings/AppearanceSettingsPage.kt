package com.nuvio.app.features.settings

import com.nuvio.app.core.ui.LocalNuvioTabletNavLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.floatingNavigationGlowSupported
import com.nuvio.app.isIos
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.labelRes
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.luxo_settings_page_fonts
import nuvio.composeapp.generated.resources.luxo_settings_page_splash
import com.nuvio.app.features.luxo.lx
import nuvio.composeapp.generated.resources.cd_selected
import nuvio.composeapp.generated.resources.collections_header
import nuvio.composeapp.generated.resources.compose_settings_page_continue_watching
import nuvio.composeapp.generated.resources.compose_settings_page_homescreen
import nuvio.composeapp.generated.resources.compose_settings_page_meta_screen
import nuvio.composeapp.generated.resources.compose_settings_page_poster_customization
import nuvio.composeapp.generated.resources.compose_settings_page_streams
import nuvio.composeapp.generated.resources.settings_appearance_app_language
import nuvio.composeapp.generated.resources.settings_appearance_app_language_restart_message
import nuvio.composeapp.generated.resources.settings_appearance_app_language_restart_title
import nuvio.composeapp.generated.resources.settings_appearance_app_language_sheet_title
import nuvio.composeapp.generated.resources.settings_appearance_app_icon
import nuvio.composeapp.generated.resources.settings_appearance_tab_bar_behavior
import nuvio.composeapp.generated.resources.settings_appearance_tab_bar_behavior_sheet_title
import nuvio.composeapp.generated.resources.settings_appearance_nav_bar_style
import nuvio.composeapp.generated.resources.settings_nav_bar_glow_on
import nuvio.composeapp.generated.resources.settings_nav_bar_glow_off
import nuvio.composeapp.generated.resources.settings_nav_bar_summary
import nuvio.composeapp.generated.resources.settings_appearance_amoled_black
import nuvio.composeapp.generated.resources.settings_appearance_amoled_description
import nuvio.composeapp.generated.resources.settings_appearance_dynamic_artwork_background
import nuvio.composeapp.generated.resources.settings_appearance_dynamic_artwork_background_description
import nuvio.composeapp.generated.resources.settings_appearance_catalog_accent
import nuvio.composeapp.generated.resources.settings_appearance_catalog_accent_description
import nuvio.composeapp.generated.resources.settings_appearance_continue_watching_description
import nuvio.composeapp.generated.resources.settings_appearance_poster_customization_description
import nuvio.composeapp.generated.resources.settings_appearance_section_detail_page
import nuvio.composeapp.generated.resources.settings_appearance_section_display
import nuvio.composeapp.generated.resources.settings_appearance_section_home
import nuvio.composeapp.generated.resources.settings_appearance_section_streams
import nuvio.composeapp.generated.resources.settings_appearance_section_theme
import nuvio.composeapp.generated.resources.settings_content_discovery_collections_description
import nuvio.composeapp.generated.resources.settings_content_discovery_homescreen_description
import nuvio.composeapp.generated.resources.settings_content_discovery_meta_screen_description
import nuvio.composeapp.generated.resources.compose_settings_root_streams_description
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.itemsIndexed
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.glass.isLiquidGlassAvailable
import nuvio.composeapp.generated.resources.settings_appearance_liquid_glass_effects
import nuvio.composeapp.generated.resources.settings_appearance_liquid_glass_effects_description

internal fun LazyListScope.appearanceSettingsContent(
    isTablet: Boolean,
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    amoledEnabled: Boolean,
    onAmoledToggle: (Boolean) -> Unit,
    dynamicArtworkBackgroundEnabled: Boolean,
    onDynamicArtworkBackgroundToggle: (Boolean) -> Unit,
    showCatalogAccentEnabled: Boolean,
    onShowCatalogAccentToggle: (Boolean) -> Unit,
    liquidGlassNativeTabBarSupported: Boolean,
    tabBarBehavior: NuvioTabBarBehavior,
    onTabBarBehaviorSelected: (NuvioTabBarBehavior) -> Unit,
    appIconState: AppIconSettingsState,
    onAppIconSelected: (AppIconOption) -> Unit,
    onAppIconFailureDismissed: () -> Unit,
    selectedAppLanguage: AppLanguage,
    onAppLanguageSelected: (AppLanguage) -> Unit,
    selectedNavBarStyle: NavBarStyle,
    onNavBarStyleSelected: (NavBarStyle) -> Unit,
    onHomescreenClick: () -> Unit,
    onMetaScreenClick: () -> Unit,
    onStreamsClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onContinueWatchingClick: () -> Unit,
    onPosterCustomizationClick: () -> Unit,
    onSplashClick: () -> Unit = {},
    onFontsClick: () -> Unit = {},
) {
    item {
        SettingsSection(
            title = "موج",
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.luxo_settings_page_splash),
                    description = lx(
                        "الخلفية المتحركة والألوان والشعار وحركته ومدة الظهور",
                        "Animated background, colours, logo, motion and duration",
                    ),
                    isTablet = isTablet,
                    onClick = onSplashClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.luxo_settings_page_fonts),
                    description = lx(
                        "خط التطبيق بالعربي والإنجليزي، وإضافة خطوطك الخاصة",
                        "App font for Arabic and English, plus your own fonts",
                    ),
                    isTablet = isTablet,
                    onClick = onFontsClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                LuxoNavTabSwitches(isTablet = isTablet)
                SettingsGroupDivider(isTablet = isTablet)
                LuxoLookControls(isTablet = isTablet)
                SettingsGroupDivider(isTablet = isTablet)
                LuxoExtrasControls(isTablet = isTablet)
            }
        }
    }
    item {
        SettingsSection(
            title = lx("شريط التنقل · موج", "Navigation · Mawj"),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                LuxoNavExtrasControls(isTablet = isTablet)
            }
        }
    }
    item {
        SettingsSection(
            title = lx("إضافات موج", "Mawj extras"),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                if (com.nuvio.app.features.luxo.rememberLuxoFeatures().themeStudio) {
                    LuxoThemeStudio(isTablet = isTablet)
                    SettingsGroupDivider(isTablet = isTablet)
                }
                LuxoFeatureSwitches(isTablet = isTablet)
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_theme),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                AppearanceThemeSelector(
                    isTablet = isTablet,
                    selectedTheme = selectedTheme,
                    onThemeSelected = onThemeSelected,
                )
            }
        }
    }
    item {
        var showLanguageSheet by remember { mutableStateOf(false) }
        var showLanguageRestartDialog by remember { mutableStateOf(false) }
        val layoutDirection = LocalLayoutDirection.current
        var showNavBarStyleSheet by remember { mutableStateOf(false) }
        var showTabBarBehaviorSheet by remember { mutableStateOf(false) }
        var showAppIconPicker by remember { mutableStateOf(false) }
        val glowEnabled by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
        val navBarPosition by ThemeSettingsRepository.navBarPosition.collectAsStateWithLifecycle()
        val navBarGlass by ThemeSettingsRepository.navBarGlass.collectAsStateWithLifecycle()
        val liquidGlassEffectsEnabled by ThemeSettingsRepository.liquidGlassEffectsEnabled.collectAsStateWithLifecycle()
        val navBarDefaultIconColorEnabled by ThemeSettingsRepository.navBarDefaultIconColorEnabled.collectAsStateWithLifecycle()
        val navBarDefaultIconColor by ThemeSettingsRepository.navBarDefaultIconColor.collectAsStateWithLifecycle()
        // Upstream pins tablets to COMPACT because there the bar is a top rail. This fork puts
        // the floating pill at the bottom on every size, so the chosen style still applies.
        val effectiveNavBarStyle = selectedNavBarStyle
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_display),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_appearance_amoled_black),
                    description = stringResource(Res.string.settings_appearance_amoled_description),
                    checked = amoledEnabled,
                    isTablet = isTablet,
                    onCheckedChange = onAmoledToggle,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_appearance_dynamic_artwork_background),
                    description = stringResource(
                        Res.string.settings_appearance_dynamic_artwork_background_description,
                    ),
                    checked = dynamicArtworkBackgroundEnabled,
                    isTablet = isTablet,
                    onCheckedChange = onDynamicArtworkBackgroundToggle,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_appearance_catalog_accent),
                    description = stringResource(Res.string.settings_appearance_catalog_accent_description),
                    checked = showCatalogAccentEnabled,
                    isTablet = isTablet,
                    onCheckedChange = onShowCatalogAccentToggle,
                )
                if (isLiquidGlassAvailable()) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_appearance_liquid_glass_effects),
                        description = stringResource(Res.string.settings_appearance_liquid_glass_effects_description),
                        checked = liquidGlassEffectsEnabled,
                        isTablet = isTablet,
                        onCheckedChange = ThemeSettingsRepository::setLiquidGlassEffectsEnabled,
                    )
                }
                if (liquidGlassNativeTabBarSupported) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_appearance_tab_bar_behavior),
                        description = stringResource(tabBarBehavior.labelRes),
                        isTablet = isTablet,
                        onClick = { showTabBarBehaviorSheet = true },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_appearance_app_icon),
                    description = stringResource(appIconState.selected.labelResource),
                    enabled = appIconState.pending == null,
                    isTablet = isTablet,
                    trailingContent = {
                        AppIconThumbnail(
                            icon = appIconState.selected,
                            modifier = Modifier.size(if (isTablet) 44.dp else 40.dp),
                            cornerRadius = if (isTablet) 11.dp else 10.dp,
                        )
                    },
                    onClick = {
                        onAppIconFailureDismissed()
                        showAppIconPicker = true
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_appearance_app_language),
                    description = stringResource(selectedAppLanguage.labelRes),
                    isTablet = isTablet,
                    onClick = { showLanguageSheet = true },
                )
                if (!liquidGlassNativeTabBarSupported || !tabBarBehavior.isEnabled) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_appearance_nav_bar_style),
                        description = if (floatingNavigationGlowSupported && effectiveNavBarStyle != NavBarStyle.CLASSIC) {
                            stringResource(
                                Res.string.settings_nav_bar_summary,
                                stringResource(effectiveNavBarStyle.labelRes),
                                stringResource(if (glowEnabled) Res.string.settings_nav_bar_glow_on else Res.string.settings_nav_bar_glow_off),
                            )
                        } else {
                            stringResource(effectiveNavBarStyle.labelRes)
                        },
                        isTablet = isTablet,
                        onClick = { showNavBarStyleSheet = true },
                    )
                }
            }
        }

        if (showLanguageSheet) {
            AppearanceLanguageBottomSheet(
                selectedLanguage = selectedAppLanguage,
                onLanguageSelected = {
                    onAppLanguageSelected(it)
                    showLanguageSheet = false
                    val newLayoutDirection = if (it.isRightToLeft()) LayoutDirection.Rtl else LayoutDirection.Ltr
                    if (isIos && newLayoutDirection != layoutDirection) showLanguageRestartDialog = true
                },
                onDismiss = { showLanguageSheet = false },
            )
        }

        if (showTabBarBehaviorSheet) {
            TabBarBehaviorBottomSheet(
                selectedBehavior = tabBarBehavior,
                onBehaviorSelected = {
                    onTabBarBehaviorSelected(it)
                    showTabBarBehaviorSheet = false
                },
                onDismiss = { showTabBarBehaviorSheet = false },
            )
        }

        NuvioStatusModal(
            title = stringResource(Res.string.settings_appearance_app_language_restart_title),
            message = stringResource(Res.string.settings_appearance_app_language_restart_message),
            isVisible = showLanguageRestartDialog,
            onConfirm = { showLanguageRestartDialog = false },
        )

        if (showAppIconPicker) {
            AppIconPicker(
                isTablet = isTablet,
                state = appIconState,
                onSelected = onAppIconSelected,
                onDismiss = {
                    onAppIconFailureDismissed()
                    showAppIconPicker = false
                },
            )
        }

        if (showNavBarStyleSheet) {
            NavigationBarLiquidGlassEffectDrafts { barEffects, selectionEffects ->
                NavigationBarSettingsSheet(
                    // Passing false keeps the style list visible on tablets: the sheet hides it when
                    // isTablet is true, which only fits upstream's tablet top rail.
                    isTablet = false,
                    tabletLayout = LocalNuvioTabletNavLayout.current,
                    selectedStyle = effectiveNavBarStyle,
                    onStyleSelected = onNavBarStyleSelected,
                    glowEnabled = glowEnabled,
                    onGlowChanged = ThemeSettingsRepository::setNavBarGlowEnabled,
                    selectedPosition = navBarPosition,
                    onPositionSelected = ThemeSettingsRepository::setNavBarPosition,
                    glassStyle = navBarGlass,
                    onGlassStyleChanged = ThemeSettingsRepository::setNavBarGlass,
                    defaultIconColorEnabled = navBarDefaultIconColorEnabled,
                    onDefaultIconColorEnabledChanged = ThemeSettingsRepository::setNavBarDefaultIconColorEnabled,
                    defaultIconColor = navBarDefaultIconColor,
                    onDefaultIconColorChanged = ThemeSettingsRepository::updateNavBarDefaultIconColor,
                    onSaveDefaultIconColor = ThemeSettingsRepository::saveNavBarDefaultIconColorSettings,
                    floatingBarEffects = barEffects,
                    onFloatingBarEffectsChanged = ThemeSettingsRepository::updateNavBarLiquidGlassBarEffects,
                    selectionLensEffects = selectionEffects,
                    onSelectionLensEffectsChanged = ThemeSettingsRepository::updateNavBarLiquidGlassSelectionEffects,
                    onSaveLiquidGlassEffectSettings = ThemeSettingsRepository::saveNavBarLiquidGlassEffectSettings,
                    onDismiss = { showNavBarStyleSheet = false },
                )
            }
        }
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_home),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_homescreen),
                    description = stringResource(Res.string.settings_content_discovery_homescreen_description),
                    isTablet = isTablet,
                    onClick = onHomescreenClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.collections_header),
                    description = stringResource(Res.string.settings_content_discovery_collections_description),
                    isTablet = isTablet,
                    onClick = onCollectionsClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_continue_watching),
                    description = stringResource(Res.string.settings_appearance_continue_watching_description),
                    isTablet = isTablet,
                    onClick = onContinueWatchingClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_poster_customization),
                    description = stringResource(Res.string.settings_appearance_poster_customization_description),
                    isTablet = isTablet,
                    onClick = onPosterCustomizationClick,
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_streams),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_streams),
                    description = stringResource(Res.string.compose_settings_root_streams_description),
                    isTablet = isTablet,
                    onClick = onStreamsClick,
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_detail_page),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_meta_screen),
                    description = stringResource(Res.string.settings_content_discovery_meta_screen_description),
                    isTablet = isTablet,
                    onClick = onMetaScreenClick,
                )
            }
        }
    }
}

private data class AppLanguageSheetOption(
    val language: AppLanguage,
    val labelRes: StringResource,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceLanguageBottomSheet(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val options = remember {
        AppLanguage.entries.map { language ->
            AppLanguageSheetOption(
                language = language,
                labelRes = language.labelRes,
            )
        }
    }

    NuvioModalBottomSheet(
        onDismissRequest = {
            coroutineScope.launch {
                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            }
        },
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.settings_appearance_app_language_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }

            items(options) { option ->
                NuvioBottomSheetActionRow(
                    title = stringResource(option.labelRes),
                    selected = option.language == selectedLanguage,
                    onClick = {
                        onLanguageSelected(option.language)
                        coroutineScope.launch {
                            dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                        }
                    },
                    trailingContent = {
                        if (option.language == selectedLanguage) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(Res.string.cd_selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabBarBehaviorBottomSheet(
    selectedBehavior: NuvioTabBarBehavior,
    onBehaviorSelected: (NuvioTabBarBehavior) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    NuvioModalBottomSheet(
        onDismissRequest = {
            coroutineScope.launch {
                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            }
        },
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.settings_appearance_tab_bar_behavior_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }

            itemsIndexed(NuvioTabBarBehavior.entries.toList()) { index, behavior ->
                if (index > 0) {
                    NuvioBottomSheetDivider()
                }
                NuvioBottomSheetActionRow(
                    title = stringResource(behavior.labelRes),
                    onClick = {
                        onBehaviorSelected(behavior)
                        coroutineScope.launch {
                            dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                        }
                    },
                    trailingContent = {
                        if (behavior == selectedBehavior) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(Res.string.cd_selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun LuxoNavTabSwitches(isTablet: Boolean) {
    val navSettings by remember {
        com.nuvio.app.features.luxo.LuxoNavSettingsRepository.ensureLoaded()
        com.nuvio.app.features.luxo.LuxoNavSettingsRepository.state
    }.collectAsStateWithLifecycle()
    SettingsSwitchRow(
        title = lx("قسم الأفلام في الشريط السفلي", "Movies tab in the bottom bar"),
        description = lx("أقسام الأفلام من إضافاتك مثل Cinemata", "Movie catalogs from your addons such as Cinemata"),
        checked = navSettings.showMovies,
        isTablet = isTablet,
        onCheckedChange = { checked ->
            com.nuvio.app.features.luxo.LuxoNavSettingsRepository.update { it.copy(showMovies = checked) }
        },
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = lx("قسم المسلسلات في الشريط السفلي", "Series tab in the bottom bar"),
        description = lx("أقسام المسلسلات من إضافاتك مثل Cinemata", "Series catalogs from your addons such as Cinemata"),
        checked = navSettings.showSeries,
        isTablet = isTablet,
        onCheckedChange = { checked ->
            com.nuvio.app.features.luxo.LuxoNavSettingsRepository.update { it.copy(showSeries = checked) }
        },
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = lx("قسم «قوائمي» في الشريط السفلي", "\"My lists\" tab in the bottom bar"),
        description = lx("القوائم الخاصة مثل «أفلام الويكند» والأعمال الأعلى تقييماً", "Your own lists and your top-rated titles"),
        checked = navSettings.showLists,
        isTablet = isTablet,
        onCheckedChange = { checked ->
            com.nuvio.app.features.luxo.LuxoNavSettingsRepository.update { it.copy(showLists = checked) }
        },
    )
}

@androidx.compose.runtime.Composable
private fun LuxoLookControls(isTablet: Boolean) {
    val look = com.nuvio.app.features.luxo.rememberLuxoLookSettings()
    val metaScreen by remember {
        com.nuvio.app.features.details.MetaScreenSettingsRepository.ensureLoaded()
        com.nuvio.app.features.details.MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    LuxoBlock(isTablet) {
        LuxoLabel(lx("تأثير البوستر عند الضغط أو التحديد", "Poster effect when pressed or focused"))
        Text(
            text = when (look.posterFocusStyle) {
                com.nuvio.app.features.luxo.LuxoPosterFocusStyle.CLEAN -> lx(
                    "بدون أي إضاءة خلف البوستر: تكبير ناعم وظل أسود عميق وإطار زجاجي رفيع، والخلفية تبقى داكنة.",
                    "No light behind posters: a soft zoom, a deep black shadow and a thin glass edge; the background stays dark.",
                )
                com.nuvio.app.features.luxo.LuxoPosterFocusStyle.AMBIENT -> lx(
                    "هالة خافتة (20%) من ألوان البوستر نفسه، والشاشة تاخذ لون البوستر بشكل داكن وهادي.",
                    "A faint (20%) glow in the poster's own colours; the screen takes a dark, quiet tint of it.",
                )
                com.nuvio.app.features.luxo.LuxoPosterFocusStyle.SPOTLIGHT -> lx(
                    "بدون توهج: البوستر يرتفع شوي ويطلع خط إضاءة صغير تحته.",
                    "No glow: the poster lifts a little and a small light line appears under it.",
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LuxoChips(
            options = com.nuvio.app.features.luxo.LuxoPosterFocusStyle.entries,
            selected = look.posterFocusStyle,
            label = { style ->
                when (style) {
                    com.nuvio.app.features.luxo.LuxoPosterFocusStyle.CLEAN -> lx("Harbor الصافي", "Harbor clean")
                    com.nuvio.app.features.luxo.LuxoPosterFocusStyle.AMBIENT -> lx("إضاءة محيطية", "Ambient glow")
                    com.nuvio.app.features.luxo.LuxoPosterFocusStyle.SPOTLIGHT -> lx("تركيز سفلي", "Spotlight")
                }
            },
            onSelect = { style ->
                com.nuvio.app.features.luxo.LuxoLookSettingsRepository.update { it.copy(posterFocusStyle = style) }
            },
        )
    }
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = lx("مانع الحرق", "Spoiler protection"),
        description = lx(
            "يغبّش صورة وعنوان ووصف الحلقات التي لم تتم مشاهدتها بعد",
            "Blurs the image, title and description of episodes you haven't watched yet",
        ),
        checked = metaScreen.blurUnwatchedEpisodes,
        isTablet = isTablet,
        onCheckedChange = { checked ->
            com.nuvio.app.features.details.MetaScreenSettingsRepository.setBlurUnwatchedEpisodes(checked)
        },
    )
}

@androidx.compose.runtime.Composable
private fun LuxoExtrasControls(isTablet: Boolean) {
    val notifications by remember {
        com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository.ensureLoaded()
        com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository.uiState
    }.collectAsStateWithLifecycle()
    var recapOpen by remember { mutableStateOf(false) }
    val autoTranslate by com.nuvio.app.features.player.LuxoSubtitleTranslator.enabled.collectAsStateWithLifecycle()
    SettingsSwitchRow(
        title = lx("ترجمة الترجمات للعربي تلقائياً ✨", "Auto-translate subtitles to Arabic ✨"),
        description = lx(
            "إذا الحلقة ما لها ترجمة عربية، رفيق موج يترجم الإنجليزية ويضيفها «عربي ✨ ترجمة موج». يحتاج مفتاح Gemini",
            "When there's no Arabic subtitle, Mawj Companion translates the English one. Needs a Gemini key",
        ),
        checked = autoTranslate,
        isTablet = isTablet,
        onCheckedChange = { com.nuvio.app.features.player.LuxoSubtitleTranslator.setEnabled(it) },
    )
    SettingsGroupDivider(isTablet = isTablet)
    val metaScreen by remember {
        com.nuvio.app.features.details.MetaScreenSettingsRepository.ensureLoaded()
        com.nuvio.app.features.details.MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    SettingsSwitchRow(
        title = lx("ألوان من البوستر 🎨", "Colors from the poster 🎨"),
        description = lx(
            "صفحة الفيلم أو المسلسل تاخذ لونها من البوستر نفسه",
            "The title page takes its colors from the artwork",
        ),
        checked = metaScreen.backgroundMode == com.nuvio.app.features.details.MetaScreenBackgroundMode.DominantColor,
        isTablet = isTablet,
        onCheckedChange = {
            com.nuvio.app.features.details.MetaScreenSettingsRepository.setBackgroundMode(
                if (it) com.nuvio.app.features.details.MetaScreenBackgroundMode.DominantColor
                else com.nuvio.app.features.details.MetaScreenBackgroundMode.Cinematic,
            )
        },
    )
    SettingsGroupDivider(isTablet = isTablet)
    val appLock by com.nuvio.app.features.luxo.LuxoAppLock.enabled.collectAsStateWithLifecycle()
    SettingsSwitchRow(
        title = lx("قفل التطبيق بالبصمة 🔒", "Lock the app 🔒"),
        description = lx(
            "يطلب البصمة أو رمز الجوال عند فتح موج أو الرجوع له بعد 30 ثانية",
            "Asks for your fingerprint or device lock when you open Mawj or return after 30 seconds",
        ),
        checked = appLock,
        isTablet = isTablet,
        onCheckedChange = { com.nuvio.app.features.luxo.LuxoAppLock.setEnabled(it) },
    )
    SettingsGroupDivider(isTablet = isTablet)
    val hijriDates by com.nuvio.app.features.luxo.LuxoDateStyle.hijri.collectAsStateWithLifecycle()
    val arabicDigits by com.nuvio.app.features.luxo.LuxoDateStyle.arabicDigits.collectAsStateWithLifecycle()

    SettingsSwitchRow(
        title = lx("التاريخ الهجري", "Hijri dates"),
        description = lx("تواريخ نزول الأفلام والحلقات بالهجري", "Show release and air dates in the Hijri calendar"),
        checked = hijriDates,
        isTablet = isTablet,
        onCheckedChange = { com.nuvio.app.features.luxo.LuxoDateStyle.setHijri(it) },
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = lx("أرقام عربية (١٢٣)", "Arabic numerals (١٢٣)"),
        description = lx("عرض أرقام التواريخ بالأرقام العربية", "Show date digits as Arabic-Indic numerals"),
        checked = arabicDigits,
        isTablet = isTablet,
        onCheckedChange = { com.nuvio.app.features.luxo.LuxoDateStyle.setArabicDigits(it) },
    )
    SettingsGroupDivider(isTablet = isTablet)

    SettingsSwitchRow(
        title = lx("تنبيه الحلقات الجديدة", "New episode alerts"),
        description = notifications.errorMessage ?: lx(
            "إشعار أول ما تنزل حلقة جديدة لأي مسلسل في المكتبة",
            "A notification when a series in your library gets a new episode",
        ),
        checked = notifications.isEnabled,
        isTablet = isTablet,
        onCheckedChange = { checked ->
            com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository.setEnabled(checked)
        },
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsNavigationRow(
        title = lx("شهرك في موج ✨", "Your month in Mawj ✨"),
        description = lx(
            "رفيق موج يلخّص مشاهدات آخر 30 يوم والذوق، ويقترح أعمال جديدة",
            "Mawj Companion sums up your last 30 days of watching and suggests what's next",
        ),
        isTablet = isTablet,
        onClick = { recapOpen = true },
    )
    if (recapOpen) {
        com.nuvio.app.features.details.LuxoCompanionSheet(
            subjectKey = "month_recap",
            subjectTitle = lx("شهرك في موج", "Your month in Mawj"),
            context = remember { com.nuvio.app.features.luxo.LuxoMonthRecap.context() },
            quickActions = listOf(
                "ملخص شهري" to "اكتب الملخص الشهري الآن.",
                "وش ذوقي؟" to "حلّل ذوقي من هذه المشاهدات في 5 نقاط قصيرة.",
                "وش أشوف بعدها؟" to "اقترح 5 أعمال تناسب ذوقي مع سطر لكل عمل.",
            ),
            questionLabel = lx("سؤال عن المشاهدات", "Ask about your watching"),
            onDismiss = { recapOpen = false },
        )
    }
}
