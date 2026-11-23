package com.musicsystemmanager.app.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicsystemmanager.app.AppContainer
import com.musicsystemmanager.app.data.Song
import com.musicsystemmanager.app.ui.components.EmptyState
import com.musicsystemmanager.app.ui.components.ListContentPadding
import com.musicsystemmanager.app.ui.components.SearchField
import com.musicsystemmanager.app.ui.components.formatDuration
import kotlinx.coroutines.launch

/** Search the whole library and tap ⊕ to add songs to the playlist one by one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsScreen(
    container: AppContainer,
    playlistId: Long,
    onDone: () -> Unit,
) {
    val repo = container.repository
    val player = container.player
    var query by rememberSaveable { mutableStateOf("") }
    val playlist by remember(playlistId) { repo.playlist(playlistId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val results by remember(query) { repo.searchSongs(query) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val inPlaylist by remember(playlistId) { repo.playlistSongIds(playlistId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val inPlaylistSet = remember(inPlaylist) { inPlaylist.toHashSet() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "Add to ${playlist?.name.orEmpty()}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = { TextButton(onClick = onDone) { Text("Done") } },
        )
        SearchField(query, { query = it }, placeholder = "Search your library")

        if (results.isEmpty()) {
            EmptyState(
                emoji = "🔍",
                title = if (query.isBlank()) "Library is empty" else "No matches",
                body = if (query.isBlank()) "Import music from the Library tab first."
                else "Nothing matches \"$query\".",
            )
        }

        LazyColumn(contentPadding = ListContentPadding) {
            items(results, key = { it.id }) { song ->
                val added = song.id in inPlaylistSet
                AddSongRow(
                    song = song,
                    added = added,
                    onToggle = {
                        container.appScope.launch {
                            if (added) repo.removeFromPlaylist(playlistId, song.id)
                            else repo.addToPlaylist(playlistId, song.id)
                        }
                    },
                    onPlay = { player.playSongs(listOf(song)) },
                )
            }
        }
    }
}

@Composable
private fun AddSongRow(song: Song, added: Boolean, onToggle: () -> Unit, onPlay: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        leadingContent = {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val duration = if (song.durationMs > 0) " • ${formatDuration(song.durationMs)}" else ""
            Text(song.artist + duration, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Preview ${song.title}")
                }
                IconButton(onClick = onToggle) {
                    if (added) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Remove ${song.title} from playlist",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            Icons.Filled.AddCircleOutline,
                            contentDescription = "Add ${song.title} to playlist",
                        )
                    }
                }
            }
        },
    )
}
