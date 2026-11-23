package com.musicsystemmanager.app.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicsystemmanager.app.AppContainer
import com.musicsystemmanager.app.data.Song
import com.musicsystemmanager.app.ui.components.AddToPlaylistSheet
import com.musicsystemmanager.app.ui.components.EmptyState
import com.musicsystemmanager.app.ui.components.ListContentPadding
import com.musicsystemmanager.app.ui.components.MenuAction
import com.musicsystemmanager.app.ui.components.SearchField
import com.musicsystemmanager.app.ui.components.SongRow
import com.musicsystemmanager.app.ui.components.toggleLike
import com.musicsystemmanager.app.ui.components.songCountLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(container: AppContainer, showMessage: (String) -> Unit) {
    val vm: LibraryViewModel = viewModel(
        factory = viewModelFactory { initializer { LibraryViewModel(container.repository) } }
    )
    val query by vm.query.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val total by vm.totalSongs.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val showRecordings by vm.showRecordings.collectAsStateWithLifecycle()
    val recordingCount by vm.recordingCount.collectAsStateWithLifecycle()
    val songsTabCount by vm.songsTabCount.collectAsStateWithLifecycle()
    val player = container.player
    val playerState by player.state.collectAsStateWithLifecycle()
    var addTarget by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { showMessage(it) } }

    // --- Import entry points ---
    val context = LocalContext.current
    val audioPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.scanDevice()
        else showMessage("Permission denied. You can still use \"Import files\" or \"Import a folder\".")
    }
    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.importFiles(uris) }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) vm.importFolder(uri) }

    val scanPhone = {
        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            vm.scanDevice()
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }
    // Re-scan quietly on open (if allowed) so new downloads show up and older entries get sorted.
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            vm.autoScanOnce()
        }
    }
    val importFiles = { filesLauncher.launch(arrayOf("audio/*")) }
    val importFolder = { folderLauncher.launch(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Library") },
            actions = { ImportMenu(onScan = scanPhone, onFiles = importFiles, onFolder = importFolder) },
        )

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        when {
            total == null -> Unit // still loading
            total == 0 -> EmptyState(
                emoji = "🎶",
                title = "Your library is empty",
                body = "Bring in your music — it's free, no ads, no account. " +
                    "Scan the phone, or pick files or a whole folder.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = scanPhone, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PhoneAndroid, null)
                        Spacer(Modifier.width(8.dp)); Text("Scan phone for music")
                    }
                    FilledTonalButton(onClick = importFiles, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.UploadFile, null)
                        Spacer(Modifier.width(8.dp)); Text("Import files")
                    }
                    OutlinedButton(onClick = importFolder, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FolderOpen, null)
                        Spacer(Modifier.width(8.dp)); Text("Import a folder")
                    }
                }
            }

            else -> {
                SearchField(
                    query, vm::setQuery,
                    placeholder = if (showRecordings) "Search recordings" else "Search songs, artists, albums",
                )
                // Songs | Recordings tabs keep voice/call recordings out of the music list.
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !showRecordings,
                        onClick = { vm.showRecordings(false) },
                        label = { Text("Songs · $songsTabCount") },
                        leadingIcon = { Icon(Icons.Filled.MusicNote, null, Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = showRecordings,
                        onClick = { vm.showRecordings(true) },
                        label = { Text("Recordings · $recordingCount") },
                        leadingIcon = { Icon(Icons.Filled.Mic, null, Modifier.size(18.dp)) },
                    )
                }
                if (songs.isEmpty()) {
                    when {
                        query.isNotBlank() ->
                            EmptyState(emoji = "🔍", title = "No matches", body = "Nothing matches \"$query\".")
                        showRecordings -> EmptyState(
                            emoji = "🎙️",
                            title = "No recordings",
                            body = "Voice memos, call recordings and voice notes found on your phone appear here, " +
                                "separate from your songs.",
                        )
                        else -> EmptyState(
                            emoji = "🎶",
                            title = "No songs yet",
                            body = "Everything found so far is on the Recordings tab. Import files or a folder to add music.",
                        )
                    }
                } else {
                    LazyColumn(contentPadding = ListContentPadding) {
                        item(key = "header") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (showRecordings) {
                                        if (songs.size == 1) "1 recording" else "${songs.size} recordings"
                                    } else songCountLabel(songs.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { player.playSongs(songs, shuffle = true) }) {
                                    Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle all")
                                }
                                FilledTonalButton(onClick = { player.playSongs(songs) }) {
                                    Icon(Icons.Filled.PlayArrow, null)
                                    Spacer(Modifier.width(4.dp)); Text("Play all")
                                }
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
                                    MenuAction("Play next") {
                                        player.playNext(song); showMessage("Playing next")
                                    },
                                    MenuAction("Add to queue") {
                                        player.addToQueue(song); showMessage("Added to queue")
                                    },
                                    if (song.isRecording) {
                                        MenuAction("This is a song (move to Songs)") { vm.setRecording(song, false) }
                                    } else {
                                        MenuAction("This is a recording (move to Recordings)") { vm.setRecording(song, true) }
                                    },
                                    MenuAction("Remove from app") { vm.removeFromLibrary(song) },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    addTarget?.let { song ->
        AddToPlaylistSheet(container, song, onDismiss = { addTarget = null }, showMessage = showMessage)
    }
}

@Composable
private fun ImportMenu(onScan: () -> Unit, onFiles: () -> Unit, onFolder: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Add, contentDescription = "Add music")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Scan phone for music") },
                leadingIcon = { Icon(Icons.Filled.PhoneAndroid, null) },
                onClick = { open = false; onScan() },
            )
            DropdownMenuItem(
                text = { Text("Import files") },
                leadingIcon = { Icon(Icons.Filled.UploadFile, null) },
                onClick = { open = false; onFiles() },
            )
            DropdownMenuItem(
                text = { Text("Import a folder") },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                onClick = { open = false; onFolder() },
            )
        }
    }
}
