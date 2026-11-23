package com.musicsystemmanager.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.musicsystemmanager.app.AppContainer
import com.musicsystemmanager.app.ui.library.LibraryScreen
import com.musicsystemmanager.app.ui.player.MiniPlayer
import com.musicsystemmanager.app.ui.player.NowPlayingScreen
import com.musicsystemmanager.app.ui.player.QueueScreen
import com.musicsystemmanager.app.update.UpdateDialog
import com.musicsystemmanager.app.ui.player.SleepTimerSheet
import com.musicsystemmanager.app.ui.playlists.LikedSongsScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.musicsystemmanager.app.ui.playlists.AddSongsScreen
import com.musicsystemmanager.app.ui.playlists.PlaylistDetailScreen
import com.musicsystemmanager.app.ui.playlists.PlaylistsScreen
import kotlinx.coroutines.launch

private object Routes {
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST = "playlist/{id}"
    const val ADD_SONGS = "playlist/{id}/add"
    const val NOW_PLAYING = "now_playing"
    const val QUEUE = "queue"
    const val LIKED = "playlists/liked"
    fun playlist(id: Long) = "playlist/$id"
    fun addSongs(id: Long) = "playlist/$id/add"
}

@Composable
fun AppRoot(container: AppContainer) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = remember {
        { msg ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(msg)
            }
        }
    }
    val playerState by container.player.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.player.errors.collect { showMessage(it) } }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onPlayer = route == Routes.NOW_PLAYING
    var showTimer by remember { mutableStateOf(false) }
    val openQueue = { nav.navigate(Routes.QUEUE) { launchSingleTop = true } }
    val openTimer = { showTimer = true }

    fun goToTab(tab: String) {
        nav.navigate(tab) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!onPlayer) {
                Column {
                    if (playerState.songId != null) {
                        MiniPlayer(
                            container = container,
                            state = playerState,
                            onOpen = { nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } },
                            onOpenQueue = openQueue,
                            onOpenTimer = openTimer,
                            showMessage = showMessage,
                        )
                    }
                    NavigationBar {
                        NavigationBarItem(
                            selected = route == Routes.LIBRARY,
                            onClick = { goToTab(Routes.LIBRARY) },
                            icon = { Icon(Icons.Filled.LibraryMusic, null) },
                            label = { Text("Library") },
                        )
                        NavigationBarItem(
                            selected = route?.startsWith("playlist") == true,
                            onClick = { goToTab(Routes.PLAYLISTS) },
                            icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            label = { Text("Playlists") },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.LIBRARY, modifier = Modifier.padding(padding)) {
            composable(Routes.LIBRARY) {
                LibraryScreen(container, showMessage)
            }
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    container,
                    onOpenPlaylist = { nav.navigate(Routes.playlist(it)) },
                    onOpenLiked = { nav.navigate(Routes.LIKED) },
                    showMessage = showMessage,
                )
            }
            composable(Routes.LIKED) {
                LikedSongsScreen(container, onBack = { nav.popBackStack() }, showMessage = showMessage)
            }
            composable(Routes.QUEUE) {
                QueueScreen(container, onBack = { nav.popBackStack() }, showMessage = showMessage)
            }
            composable(
                Routes.PLAYLIST,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                PlaylistDetailScreen(
                    container, id,
                    onBack = { nav.popBackStack() },
                    onAddSongs = { nav.navigate(Routes.addSongs(id)) },
                    showMessage = showMessage,
                )
            }
            composable(
                Routes.ADD_SONGS,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                AddSongsScreen(container, id, onDone = { nav.popBackStack() })
            }
            composable(Routes.NOW_PLAYING) {
                NowPlayingScreen(
                    container,
                    onClose = { nav.popBackStack() },
                    onOpenQueue = openQueue,
                    onOpenTimer = openTimer,
                    showMessage = showMessage,
                )
            }
        }
    }

    // "New version available" pop-up: checked quietly once a day when the app opens.
    LaunchedEffect(Unit) { container.updates.checkOnStartup() }
    val update by container.updates.available.collectAsStateWithLifecycle()
    update?.let { u ->
        UpdateDialog(
            update = u,
            currentVersion = container.updates.currentVersion,
            onLater = container.updates::dismiss,
            onSkip = { container.updates.skip(u.version) },
            showMessage = showMessage,
        )
    }

    if (showTimer) {
        SleepTimerSheet(
            player = container.player,
            endsAt = playerState.sleepTimerEndsAt,
            onDismiss = { showTimer = false },
            showMessage = showMessage,
        )
    }
}
