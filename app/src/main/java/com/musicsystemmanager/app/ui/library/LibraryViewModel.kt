package com.musicsystemmanager.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsystemmanager.app.data.MusicRepository
import com.musicsystemmanager.app.data.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val repo: MusicRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** false = Songs tab, true = Recordings tab. */
    private val _showRecordings = MutableStateFlow(false)
    val showRecordings: StateFlow<Boolean> = _showRecordings.asStateFlow()

    val songs: StateFlow<List<Song>> = combine(_query, _showRecordings) { q, rec -> q to rec }
        .flatMapLatest { (q, rec) -> repo.searchSongs(q, recordings = rec) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null until the database answers, so we don't flash the empty screen. */
    val totalSongs: StateFlow<Int?> = repo.songCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recordingCount: StateFlow<Int> = repo.recordingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val songsTabCount: StateFlow<Int> = repo.songsTabCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun showRecordings(show: Boolean) {
        _showRecordings.value = show
    }

    fun setRecording(song: Song, isRecording: Boolean) = viewModelScope.launch {
        repo.setRecording(song.id, isRecording)
        _messages.tryEmit(if (isRecording) "Moved to Recordings" else "Moved to Songs")
    }

    private var autoScanned = false

    /** Quietly re-scan when the Library opens, to pick up new downloads and sort old entries. */
    fun autoScanOnce() {
        if (autoScanned || _busy.value) return
        autoScanned = true
        viewModelScope.launch {
            val added = runCatching { repo.scanDevice() }.getOrDefault(0)
            if (added > 0) _messages.tryEmit(if (added == 1) "Found 1 new song" else "Found $added new songs")
        }
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun setQuery(q: String) {
        _query.value = q
    }

    fun scanDevice() = runImport("Scanning your phone…") { repo.scanDevice() }
    fun importFiles(uris: List<Uri>) = runImport("Importing…") { repo.importFiles(uris) }
    fun importFolder(uri: Uri) = runImport("Importing folder…") { repo.importFolder(uri) }

    fun removeFromLibrary(song: Song) = viewModelScope.launch {
        repo.removeFromLibrary(song)
        _messages.tryEmit("Removed \"${song.title}\" from the app (file not deleted)")
    }

    private fun runImport(startMessage: String, block: suspend () -> Int) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _messages.tryEmit(startMessage)
            val result = runCatching { block() }
            _busy.value = false
            _messages.tryEmit(
                result.fold(
                    onSuccess = { added ->
                        when (added) {
                            0 -> "No new songs found"
                            1 -> "Added 1 new song"
                            else -> "Added $added new songs"
                        }
                    },
                    onFailure = { "Import failed: ${it.message ?: "unknown error"}" },
                )
            )
        }
    }
}
