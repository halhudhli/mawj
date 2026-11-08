package com.nuvio.app.features.luxo

import androidx.compose.ui.window.DialogProperties

actual fun luxoFullScreenDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
