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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicsystemmanager.app.AppContainer
import com.musicsystemmanager.app.data.Song
import com.musicsystemmanager.app.ui.components.AddToPlaylistSheet
import com.musicsystemmanager.app.ui.components.EmptyState
import com.musicsystemmanager.app.ui.components.ListContentPadding
import com.musicsystemmanager.app.ui.components.MenuAction
import com.musicsystemmanager.app.ui.components.PlaylistBadge
import com.musicsystemmanager.app.ui.components.SongRow
import com.musicsystemmanager.app.ui.components.formatDuration
import com.musicsystemmanager.app.ui.components.songCountLabel
import com.musicsystemmanager.app.ui.components.toggleLike

const val LIKED_SONGS_NAME = "👍 Liked songs"

/** The built-in playlist of every song the user gave a 👍. It can't be renamed or deleted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedSongsScreen(container: AppContainer, onBack: () -> Unit, showMessage: (String) -> Unit) {
    val player = container.player
    val songs by container.repository.likedSongs.collectAsStateWithLifecycle(initialValue = emptyList())
    val playerState by player.state.collectAsStateWithLifecycle()
    var addTarget by remember { mutableStateOf<Song?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
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
                        PlaylistBadge(LIKED_SONGS_NAME, size = 72)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Liked songs", style = MaterialTheme.typography.headlineSmall)
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
                    }
                }
            }

            if (songs.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        emoji = "👍",
                        title = "No liked songs yet",
                        body = "Tap 👍 next to any song, or on the player screen, and it shows up here.",
                    )
                }
            }

            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = playerState.songId == song.id,
                    onPlay = { player.playSongs(songs, index) },
                    onAddToPlaylist = { addTarget = song },
                    onToggleLike = { container.toggleLike(song.id, showMessage) },
                    menu = listOf(
                        MenuAction("Play next") { player.playNext(song); showMessage("Playing next") },
                        MenuAction("Add to queue") { player.addToQueue(song); showMessage("Added to queue") },
                    ),
                )
            }
        }
    }

    addTarget?.let { song ->
        AddToPlaylistSheet(container, song, onDismiss = { addTarget = null }, showMessage = showMessage)
    }
}
