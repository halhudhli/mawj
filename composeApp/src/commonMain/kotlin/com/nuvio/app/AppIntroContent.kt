package com.nuvio.app

import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import com.nuvio.app.features.luxo.installBundledFontsForSubtitles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.splash.LuxoSplashScreen
import com.nuvio.app.features.splash.SplashSettingsRepository

/** Default hold for the launch intro; the user's splash settings override it (see AppGate). */
internal const val AppIntroMinDurationMs = 1300L

@Composable
internal fun AppIntroContent(modifier: Modifier = Modifier) {
    val settings by remember {
        SplashSettingsRepository.ensureLoaded()
        SplashSettingsRepository.state
    }.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            runCatching { installBundledFontsForSubtitles() }
        }
    }

    LuxoSplashScreen(
        settings = settings,
        modifier = if (settings.tapToSkip) {
            modifier.then(
                androidx.compose.ui.Modifier.clickable(
                    interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { com.nuvio.app.features.splash.SplashSkip.requested = true },
            )
        } else {
            modifier
        },
    )
}
