package com.nuvio.app.core.ui

import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.theme_amber
import nuvio.composeapp.generated.resources.theme_crimson
import nuvio.composeapp.generated.resources.theme_emerald
import nuvio.composeapp.generated.resources.theme_ocean
import nuvio.composeapp.generated.resources.theme_rose
import nuvio.composeapp.generated.resources.theme_violet
import nuvio.composeapp.generated.resources.theme_white
import nuvio.composeapp.generated.resources.theme_mawj_sea
import nuvio.composeapp.generated.resources.theme_mawj_dawn
import nuvio.composeapp.generated.resources.theme_mawj_dunes
import nuvio.composeapp.generated.resources.theme_mawj_aurora
import nuvio.composeapp.generated.resources.theme_mawj_reef
import nuvio.composeapp.generated.resources.theme_mawj_galaxy
import nuvio.composeapp.generated.resources.theme_mawj_sadu
import nuvio.composeapp.generated.resources.theme_gold
import nuvio.composeapp.generated.resources.theme_jade
import nuvio.composeapp.generated.resources.theme_rose_gold
import nuvio.composeapp.generated.resources.theme_arctic_blue
import nuvio.composeapp.generated.resources.theme_graphite
import nuvio.composeapp.generated.resources.theme_custom
import org.jetbrains.compose.resources.StringResource

enum class AppTheme {
    GOLD,
    JADE,
    ROSE_GOLD,
    ARCTIC_BLUE,
    GRAPHITE,
    CUSTOM,
    CRIMSON,
    OCEAN,
    VIOLET,
    EMERALD,
    AMBER,
    ROSE,
    WHITE,
    /** Mawj's own: deep night-sea blue with the moon's gold. */
    MAWJ_SEA,
    /** Mawj's worlds: each one a palette and a living background of its own. */
    MAWJ_DAWN,
    MAWJ_DUNES,
    MAWJ_AURORA,
    MAWJ_REEF,
    MAWJ_GALAXY,
    MAWJ_SADU,
}

val AppTheme.labelRes: StringResource
    get() = when (this) {
        AppTheme.GOLD -> Res.string.theme_gold
        AppTheme.JADE -> Res.string.theme_jade
        AppTheme.ROSE_GOLD -> Res.string.theme_rose_gold
        AppTheme.ARCTIC_BLUE -> Res.string.theme_arctic_blue
        AppTheme.GRAPHITE -> Res.string.theme_graphite
        AppTheme.CUSTOM -> Res.string.theme_custom
        AppTheme.CRIMSON -> Res.string.theme_crimson
        AppTheme.OCEAN -> Res.string.theme_ocean
        AppTheme.VIOLET -> Res.string.theme_violet
        AppTheme.EMERALD -> Res.string.theme_emerald
        AppTheme.AMBER -> Res.string.theme_amber
        AppTheme.ROSE -> Res.string.theme_rose
        AppTheme.WHITE -> Res.string.theme_white
        AppTheme.MAWJ_SEA -> Res.string.theme_mawj_sea
        AppTheme.MAWJ_DAWN -> Res.string.theme_mawj_dawn
        AppTheme.MAWJ_DUNES -> Res.string.theme_mawj_dunes
        AppTheme.MAWJ_AURORA -> Res.string.theme_mawj_aurora
        AppTheme.MAWJ_REEF -> Res.string.theme_mawj_reef
        AppTheme.MAWJ_GALAXY -> Res.string.theme_mawj_galaxy
        AppTheme.MAWJ_SADU -> Res.string.theme_mawj_sadu
    }
