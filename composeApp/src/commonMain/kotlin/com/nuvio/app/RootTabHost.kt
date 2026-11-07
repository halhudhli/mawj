package com.nuvio.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.rememberLifecycleOwner
import com.nuvio.app.core.ui.LocalScreenActive

@Composable
internal fun RootTabHost(
    selectedTab: AppScreenTab,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    profileId: Int? = null,
    content: @Composable (AppScreenTab) -> Unit,
) {
    val parentLifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val hostActive = active && parentLifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val focusManager = LocalFocusManager.current
    DisposableEffect(selectedTab, hostActive, profileId) {
        onDispose { focusManager.clearFocus(force = true) }
    }

    key(profileId) {
        val tabStateHolder = rememberSaveableStateHolder()
        val visitedTabs = remember { mutableSetOf<AppScreenTab>() }
        val displayedTabs = remember(selectedTab) { (visitedTabs + selectedTab).toList() }
        SideEffect { visitedTabs += selectedTab }

        Layout(
            modifier = modifier.fillMaxSize(),
            content = {
                displayedTabs.forEach { tab ->
                    key(tab) {
                        RootTabPane(
                            tab = tab,
                            active = hostActive && tab == selectedTab,
                            stateHolder = tabStateHolder,
                            content = content,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val placeable = measurables[displayedTabs.indexOf(selectedTab)].measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }
    }
}

@Composable
private fun RootTabPane(
    tab: AppScreenTab,
    active: Boolean,
    stateHolder: SaveableStateHolder,
    content: @Composable (AppScreenTab) -> Unit,
) {
    val lifecycleOwner = rememberLifecycleOwner(
        maxLifecycle = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED,
    )
    // Luxo TV: when a tab opens (or Home comes back after Details), the remote's focus goes into
    // the tab and returns to the card it was on, instead of disappearing.
    val tvRemote = com.nuvio.app.features.luxo.rememberLuxoTvRemote()
    val paneFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var paneHasFocus by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(active, tvRemote) {
        if (!active || !tvRemote) return@LaunchedEffect
        repeat(16) {
            kotlinx.coroutines.delay(200)
            if (paneHasFocus) return@LaunchedEffect
            runCatching { paneFocus.requestFocus() }
        }
    }
    CompositionLocalProvider(
        LocalLifecycleOwner provides lifecycleOwner,
        LocalScreenActive provides active,
    ) {
        stateHolder.SaveableStateProvider(tab.name) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer()
                    .then(
                        if (tvRemote) {
                            Modifier
                                .onFocusChanged { paneHasFocus = it.hasFocus }
                                .focusRequester(paneFocus)
                                .focusRestorer()
                        } else {
                            Modifier
                        },
                    )
                    .focusProperties { canFocus = active }
                    .pointerInput(active) {
                        if (!active) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                    .then(if (active) Modifier else Modifier.clearAndSetSemantics {}),
            ) {
                content(tab)
            }
        }
    }
}
