package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual fun luxoOpenYoutubeApp(): Boolean = false

@Composable
actual fun LuxoYoutubeWeb(modifier: Modifier, onBackHandler: ((() -> Boolean)) -> Unit) {}

actual fun luxoOpenYoutubeAdFree(): Boolean = false
