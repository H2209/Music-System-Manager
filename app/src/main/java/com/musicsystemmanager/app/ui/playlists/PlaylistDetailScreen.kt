package com.musicsystemmanager.app.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicsystemmanager.app.AppContainer
import com.musicsystemmanager.app.data.Song
import com.musicsystemmanager.app.ui.components.AddToPlaylistSheet
import com.musicsystemmanager.app.ui.components.EmptyState
import com.musicsystemmanager.app.ui.components.ListContentPadding
import com.musicsystemmanager.app.ui.components.MenuAction
import com.musicsystemmanager.app.ui.components.OverflowMenu
import com.musicsystemmanager.app.ui.components.PlaylistBadge
import com.musicsystemmanager.app.ui.components.PlaylistNameDialog
import com.musicsystemmanager.app.ui.components.SongRow
import com.musicsystemmanager.app.ui.components.toggleLike
import com.musicsystemmanager.app.ui.components.formatDuration
import com.musicsystemmanager.app.ui.components.songCountLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    container: AppContainer,
    playlistId: Long,
    onBack: () -> Unit,
    onAddSongs: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val repo = container.repository
    val player = container.player
    val playlist by remember(playlistId) { repo.playlist(playlistId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val songs by remember(playlistId) { repo.playlistSongs(playlistId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playerState by player.state.collectAsStateWithLifecycle()

    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<Song?>(null) }

    val name = playlist?.name.orEmpty()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {}, // the name is shown large in the header below
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                OverflowMenu(
                    listOf(
                        MenuAction("Rename") { renaming = true },
                        MenuAction("Delete playlist") { deleting = true },
                    )
                )
            },
        )

        LazyColumn(contentPadding = ListContentPadding) {
            item(key = "header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlaylistBadge(name, size = 72)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.headlineSmall)
                            val totalMs = songs.sumOf { it.durationMs }
                            Text(
                                songCountLabel(songs.size) +
                                    if (totalMs > 0) " • ${formatDuration(totalMs)}" else "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { player.playSongs(songs) }, enabled = songs.isNotEmpty()) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(4.dp)); Text("Play")
                        }
                        FilledTonalButton(
                            onClick = { player.playSongs(songs, shuffle = true) },
                            enabled = songs.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Shuffle, null)
                            Spacer(Modifier.width(4.dp)); Text("Shuffle")
                        }
                        OutlinedButton(onClick = onAddSongs) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(4.dp)); Text("Add", maxLines = 1)
                        }
                    }
                }
            }

            if (songs.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        emoji = "➕",
                        title = "This playlist is empty",
                        body = "Tap \"Add\" to search your library and add songs one by one.",
                    )
                }
            }

            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = playerState.songId == song.id,
                    isPlaying = playerState.isPlaying,
                    onTogglePlayPause = player::togglePlayPause,
                    onPlay = { player.playSongs(songs, index) },
                    onAddToPlaylist = { addTarget = song },
                    onToggleLike = { container.toggleLike(song.id, showMessage) },
                    menu = listOf(
                        MenuAction("Play next") { player.playNext(song); showMessage("Playing next") },
                        MenuAction("Remove from playlist") {
                            container.appScope.launch {
                                repo.removeFromPlaylist(playlistId, song.id)
                                showMessage("Removed from $name")
                            }
                        },
                    ),
                )
            }
        }
    }

    if (renaming) {
        PlaylistNameDialog(
            title = "Rename playlist",
            confirmLabel = "Save",
            initialName = name,
            onDismiss = { renaming = false },
            onConfirm = { newName ->
                renaming = false
                container.appScope.launch { repo.renamePlaylist(playlistId, newName) }
            },
        )
    }

    if (deleting) {
        DeletePlaylistDialog(
            name = name,
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                container.appScope.launch {
                    repo.deletePlaylist(playlistId)
                    showMessage("Deleted \"$name\"")
                }
                onBack()
            },
        )
    }

    addTarget?.let { song ->
        AddToPlaylistSheet(container, song, onDismiss = { addTarget = null }, showMessage = showMessage)
    }
}
