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

/** Default hold for the launch intro; the user's splash settings override it (see AppGate). */
internal const val AppIntroMinDurationMs = 1300L

@Composable
internal fun AppIntroContent(modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            runCatching { installBundledFontsForSubtitles() }
        }
    }

    // «موج» opener, chosen in Settings → المظهر → شاشة الدخول. A tap skips it.
    val style = com.nuvio.app.features.splash.MawjIdentPrefs.forThisLaunch ?: return
    com.nuvio.app.features.splash.MawjIdent(
        style = style,
        modifier = modifier.then(
            androidx.compose.ui.Modifier.clickable(
                interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { com.nuvio.app.features.splash.SplashSkip.requested = true },
        ),
    )
}
