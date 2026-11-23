package com.musicsystemmanager.app.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicsystemmanager.app.AppContainer
import com.musicsystemmanager.app.data.Playlist
import com.musicsystemmanager.app.ui.components.EmptyState
import com.musicsystemmanager.app.ui.components.MenuAction
import com.musicsystemmanager.app.ui.components.OverflowMenu
import com.musicsystemmanager.app.ui.components.PlaylistBadge
import com.musicsystemmanager.app.ui.components.PlaylistNameDialog
import com.musicsystemmanager.app.ui.components.songCountLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    container: AppContainer,
    onOpenPlaylist: (Long) -> Unit,
    onOpenLiked: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val repo = container.repository
    val playlists by repo.playlists.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Playlists") })
            val list = playlists
            val likedCount by repo.likedCount.collectAsStateWithLifecycle(initialValue = 0)
            if (list != null) LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                // Built-in, always first.
                item(key = "liked") {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenLiked),
                        leadingContent = { PlaylistBadge(LIKED_SONGS_NAME) },
                        headlineContent = { Text("Liked songs") },
                        supportingContent = { Text(songCountLabel(likedCount)) },
                    )
                }
                if (list.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            emoji = "📀",
                            title = "No playlists yet",
                            body = "Create one and name it anything you like — emojis welcome 🔥🎧",
                        )
                    }
                }
                run {
                    items(list, key = { it.playlist.id }) { item ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenPlaylist(item.playlist.id) },
                            leadingContent = { PlaylistBadge(item.playlist.name) },
                            headlineContent = {
                                Text(item.playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = { Text(songCountLabel(item.songCount)) },
                            trailingContent = {
                                OverflowMenu(
                                    listOf(
                                        MenuAction("Rename") { renaming = item.playlist },
                                        MenuAction("Delete") { deleting = item.playlist },
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("New playlist") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (creating) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                scope.launch {
                    val id = repo.createPlaylist(name)
                    onOpenPlaylist(id)
                }
            },
        )
    }

    renaming?.let { playlist ->
        PlaylistNameDialog(
            title = "Rename playlist",
            confirmLabel = "Save",
            initialName = playlist.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                container.appScope.launch { repo.renamePlaylist(playlist.id, name) }
            },
        )
    }

    deleting?.let { playlist ->
        DeletePlaylistDialog(
            name = playlist.name,
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                container.appScope.launch {
                    repo.deletePlaylist(playlist.id)
                    showMessage("Deleted \"${playlist.name}\"")
                }
            },
        )
    }
}

@Composable
fun DeletePlaylistDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete playlist?") },
        text = { Text("\"$name\" will be deleted. Your songs stay in the library.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
