package com.musicsystemmanager.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import kotlinx.coroutines.launch

/** Bottom sheet: pick a playlist for [song], or create a new one right here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    container: AppContainer,
    song: Song,
    onDismiss: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val repo = container.repository
    val playlists by repo.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create & add",
            onDismiss = { creating = false },
            onConfirm = { name ->
                container.appScope.launch {
                    val id = repo.createPlaylist(name)
                    repo.addToPlaylist(id, song.id)
                    showMessage("Created \"$name\" and added the song")
                }
                onDismiss()
            },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            "Add \"${song.title}\" to…",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.navigationBarsPadding()) {
            item {
                ListItem(
                    modifier = Modifier.clickable { creating = true },
                    leadingContent = {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    },
                    headlineContent = { Text("New playlist") },
                )
            }
            items(playlists, key = { it.playlist.id }) { item ->
                ListItem(
                    modifier = Modifier.clickable {
                        val name = item.playlist.name
                        container.appScope.launch {
                            val added = repo.addToPlaylist(item.playlist.id, song.id)
                            showMessage(if (added) "Added to $name" else "Already in $name")
                        }
                        onDismiss()
                    },
                    leadingContent = { PlaylistBadge(item.playlist.name) },
                    headlineContent = {
                        Text(item.playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text(songCountLabel(item.songCount)) },
                )
            }
        }
    }
}

fun songCountLabel(count: Int) = if (count == 1) "1 song" else "$count songs"
