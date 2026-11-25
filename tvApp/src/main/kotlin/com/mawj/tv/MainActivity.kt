@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mawj.tv

import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.allowRgb565
import coil3.request.crossfade
import com.mawj.tv.data.Episode
import com.mawj.tv.data.MetaPreview
import com.mawj.tv.data.PlayRequest
import com.mawj.tv.data.Store
import com.mawj.tv.player.PlayerScreen
import com.mawj.tv.ui.AppBackdrop
import com.mawj.tv.ui.DetailScreen
import com.mawj.tv.ui.HomeScreen
import com.mawj.tv.ui.LibraryScreen
import com.mawj.tv.ui.Rail
import com.mawj.tv.ui.SearchScreen
import com.mawj.tv.ui.SettingsScreen
import com.mawj.tv.ui.SourcesScreen
import okio.Path.Companion.toOkioPath

class MawjTv : Application(), SingletonImageLoader.Factory {
    companion object {
        lateinit var app: MawjTv
            private set
    }

    override fun onCreate() {
        super.onCreate()
        app = this
        Store.init(this)
        com.mawj.tv.data.Library.init(this)
        com.mawj.tv.data.Account.init(this)
        com.mawj.tv.data.Account.syncOnStart()
        com.mawj.tv.link.PhoneLink.start()
        com.mawj.tv.link.RemoteLink.start(this)
    }

    /** Small memory cache and a disk cache; full colour (posters must stay sharp, no banding). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.05).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img").toOkioPath()).maxSizeBytes(150L * 1024 * 1024).build() }
            .allowRgb565(false)
            .crossfade(false)
            .build()
}

sealed interface Route {
    data object Home : Route
    data object Search : Route
    data object Library : Route
    data object Settings : Route
    data class Detail(val meta: MetaPreview) : Route
    data class Sources(val meta: MetaPreview, val episode: Episode?) : Route
    data class Player(val request: PlayRequest) : Route
    data class Person(val id: Int, val name: String) : Route
    data object Login : Route
    data class Company(val id: Int, val name: String) : Route
    data object Movies : Route
    data object Series : Route
    data object Islands : Route
    data object Lists : Route
    data object Profile : Route
    data class Works(val title: String, val island: com.mawj.tv.data.Island) : Route
    data class SettingsPage(val page: String) : Route
    data class Tool(val kind: String) : Route
}

/** A tiny back stack; the top-level pages (with the side rail) replace each other. */
object Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val top: Route get() = stack.last()

    fun go(route: Route) {
        if (route is Route.Player && top is Route.Player) stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    fun tab(route: Route) {
        stack.clear()
        stack.add(route)
    }

    fun back(): Boolean {
        if (stack.size <= 1) {
            if (top != Route.Home) { tab(Route.Home); return true }
            return false
        }
        stack.removeAt(stack.lastIndex)
        return true
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        com.mawj.tv.link.RemoteLink.activity = java.lang.ref.WeakReference(this)
        // First start: Mawj's sign-in page (it can be skipped).
        if (savedInstanceState == null && !com.mawj.tv.data.Account.introSeen && com.mawj.tv.data.Account.configured) Nav.tab(Route.Login)
        setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides com.mawj.tv.ui.PivotScroll,
            ) {
                MawjApp(onExit = { finish() })
            }
        }
    }
}

@Composable
private fun MawjApp(onExit: () -> Unit) {
    val route = Nav.top
    val holder = rememberSaveableStateHolder()
    BackHandler { if (!Nav.back()) onExit() }
    Box(Modifier.fillMaxSize()) {
        val sea = route is Route.Islands || route is Route.Profile || route is Route.Settings || route is Route.SettingsPage ||
            route is Route.Lists || route is Route.Tool || route is Route.Company
        when {
            route is Route.Player -> Unit
            sea -> com.mawj.tv.ui.SeaBackground()
            else -> AppBackdrop(soft = route is Route.Sources, clear = route is Route.Home || route is Route.Movies || route is Route.Series)
        }
        val topLevel = route is Route.Home || route is Route.Search || route is Route.Library || route is Route.Settings ||
            route is Route.Movies || route is Route.Series || route is Route.Islands || route is Route.Lists || route is Route.Profile
        Row(Modifier.fillMaxSize()) {
            if (topLevel) Rail(route)
            Box(Modifier.weight(1f).fillMaxSize()) {
                holder.SaveableStateProvider(route.toString()) {
                    when (route) {
                        Route.Home -> HomeScreen()
                        Route.Search -> SearchScreen()
                        Route.Library -> LibraryScreen()
                        Route.Settings -> SettingsScreen()
                        is Route.Detail -> DetailScreen(route.meta)
                        is Route.Sources -> SourcesScreen(route.meta, route.episode)
                        is Route.Player -> PlayerScreen(route.request)
                        Route.Login -> com.mawj.tv.ui.LoginScreen()
                        is Route.Person -> com.mawj.tv.ui.PersonScreen(route.id, route.name)
                        is Route.Company -> com.mawj.tv.ui.CompanyScreen(route.id, route.name)
                        Route.Movies -> com.mawj.tv.ui.BrowseScreen("movie")
                        Route.Series -> com.mawj.tv.ui.BrowseScreen("tv")
                        Route.Islands -> com.mawj.tv.ui.IslandsScreen()
                        Route.Lists -> com.mawj.tv.ui.ListsScreen()
                        Route.Profile -> com.mawj.tv.ui.ProfileScreen()
                        is Route.Works -> com.mawj.tv.ui.WorksScreen(route.title, route.island)
                        is Route.SettingsPage -> com.mawj.tv.ui.SettingsPageScreen(route.page)
                        is Route.Tool -> com.mawj.tv.ui.ToolScreen(route.kind)
                    }
                }
            }
        }
        com.mawj.tv.ui.PairingOverlay()
    }
}
