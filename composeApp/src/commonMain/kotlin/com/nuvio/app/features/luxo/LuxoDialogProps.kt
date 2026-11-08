package com.nuvio.app.features.luxo

import androidx.compose.ui.window.DialogProperties

/** Full-screen dialog properties (edge to edge on Android; iOS has no window decor to opt out of). */
expect fun luxoFullScreenDialogProperties(): DialogProperties
