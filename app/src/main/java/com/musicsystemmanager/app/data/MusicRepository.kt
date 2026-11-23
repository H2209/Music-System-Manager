package com.musicsystemmanager.app.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MusicRepository(
    context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
) {
    private val context = context.applicationContext
    private val resolver get() = context.contentResolver

    // ---------- Library ----------

    // Library lists hide extra copies of the same song (see Duplicates).
    fun searchSongs(query: String): Flow<List<Song>> =
        songDao.search(query.trim()).map(Duplicates::hide)
    fun searchSongs(query: String, recordings: Boolean): Flow<List<Song>> =
        songDao.searchKind(query.trim(), recordings).map(Duplicates::hide)
    val songCount: Flow<Int> = songDao.count()
    /** Counts for the Songs / Recordings tabs, after hiding duplicates. */
    val songsTabCount: Flow<Int> = searchSongs("", recordings = false).map { it.size }
    val recordingCount: Flow<Int> = searchSongs("", recordings = true).map { it.size }
    suspend fun song(id: Long): Song? = songDao.byId(id)

    /** Lets the user fix a wrong guess: move a file between Songs and Recordings. */
    suspend fun setRecording(songId: Long, isRecording: Boolean) =
        songDao.setKind(songId, if (isRecording) Song.KIND_RECORDING else Song.KIND_SONG)

    // ---------- Liked songs ----------

    val likedSongs: Flow<List<Song>> = songDao.liked()
    val likedCount: Flow<Int> = songDao.likedCount()
    fun isLiked(songId: Long): Flow<Boolean> = songDao.likedFlag(songId).map { it > 0 }

    /** Returns true if the song is now liked. */
    suspend fun toggleLike(songId: Long): Boolean = songDao.toggleLike(songId)

    /**
     * Finds every music file the phone already knows about (MediaStore).
     * Needs READ_MEDIA_AUDIO (Android 13+) or READ_EXTERNAL_STORAGE (Android 10-12).
     * Returns how many new songs were added.
     */
    suspend fun scanDevice(): Int = withContext(Dispatchers.IO) {
        val collection =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        val sdk = android.os.Build.VERSION.SDK_INT
        // Folder of the file: RELATIVE_PATH on Android 10+, full path (DATA) on 8-9.
        @Suppress("DEPRECATION")
        val pathColumn = if (sdk >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        @android.annotation.SuppressLint("InlinedApi") // only used when sdk >= 31
        val isRecordingColumn = if (sdk >= 31) MediaStore.Audio.Media.IS_RECORDING else null
        val projection = listOfNotNull(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            pathColumn,
            isRecordingColumn,
        ).toTypedArray()
        // Music, plus anything Android 12+ flags as a recording (shown on the Recordings tab).
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            if (isRecordingColumn != null) append(" OR $isRecordingColumn != 0")
        }
        val found = mutableListOf<Song>()
        resolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val pathCol = c.getColumnIndexOrThrow(pathColumn)
            val recCol = isRecordingColumn?.let { c.getColumnIndexOrThrow(it) }
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                val displayName = c.getString(nameCol)
                val fileName = displayName?.substringBeforeLast('.') ?: "Unknown"
                val kind = AudioClassifier.classify(
                    path = c.getString(pathCol),
                    fileName = displayName,
                    mimeType = c.getString(mimeCol),
                    flaggedAsRecording = recCol != null && c.getInt(recCol) != 0,
                )
                found += Song(
                    uri = uri.toString(),
                    title = c.getString(titleCol).orIfBlank(fileName),
                    artist = c.getString(artistCol).cleanArtist(),
                    album = c.getString(albumCol).orEmpty(),
                    durationMs = c.getLong(durCol),
                    source = Song.SOURCE_DEVICE,
                    kind = kind,
                )
            }
        }
        val added = songDao.insertAll(found).count { it != -1L }
        // Songs saved by older versions have no type yet: fill it in (manual choices are kept).
        songDao.fillMissingKinds(found)

        // Drop songs that were deleted from the phone since the last scan.
        val present = found.mapTo(HashSet()) { it.uri }
        val gone = songDao.deviceUris().filterNot { it in present }
        if (gone.isNotEmpty()) songDao.deleteByUris(gone)
        added
    }

    /** Imports individual files picked with the system file picker. */
    suspend fun importFiles(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val songs = uris.map { uri ->
            keepReadAccess(uri)
            readSong(uri)
        }
        songDao.insertAll(songs).count { it != -1L }
    }

    /** Imports every audio file inside a folder (and its sub-folders). */
    suspend fun importFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        keepReadAccess(treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
        val files = mutableListOf<DocumentFile>()
        collectAudio(root, files, depth = 0)
        val songs = files.map { readSong(it.uri, it.name) }
        songDao.insertAll(songs).count { it != -1L }
    }

    /** Removes from the app only — never deletes the file from the phone. */
    suspend fun removeFromLibrary(song: Song) {
        songDao.delete(song.id)
        if (song.source == Song.SOURCE_IMPORTED) {
            runCatching {
                resolver.releasePersistableUriPermission(
                    Uri.parse(song.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    // ---------- Playlists ----------

    val playlists: Flow<List<PlaylistWithCount>> = playlistDao.playlistsWithCount()
    fun playlist(id: Long): Flow<Playlist?> = playlistDao.playlist(id)
    fun playlistSongs(id: Long): Flow<List<Song>> = playlistDao.songs(id)
    fun playlistSongIds(id: Long): Flow<List<Long>> = playlistDao.songIds(id)

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insert(Playlist(name = name.trim()))

    suspend fun renamePlaylist(id: Long, name: String) = playlistDao.rename(id, name.trim())
    suspend fun deletePlaylist(id: Long) = playlistDao.delete(id)
    suspend fun addToPlaylist(playlistId: Long, songId: Long): Boolean =
        playlistDao.addSong(playlistId, songId)

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) =
        playlistDao.removeSong(playlistId, songId)

    // ---------- Helpers ----------

    private fun keepReadAccess(uri: Uri) {
        // Lets us keep playing the file after the phone restarts.
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun collectAudio(dir: DocumentFile, out: MutableList<DocumentFile>, depth: Int) {
        if (depth > 10) return
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                collectAudio(file, out, depth + 1)
            } else if (file.isAudio()) {
                out += file
            }
        }
    }

    private fun DocumentFile.isAudio(): Boolean {
        if (type?.startsWith("audio/") == true) return true
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return ext in AUDIO_EXTENSIONS
    }

    private fun readSong(uri: Uri, knownName: String? = null, folderPath: String? = null): Song {
        val name = knownName ?: displayName(uri)
        val fallbackTitle = name?.substringBeforeLast('.') ?: "Unknown"
        val kind = AudioClassifier.classify(
            path = folderPath ?: Uri.decode(uri.toString()),
            fileName = name,
            mimeType = runCatching { resolver.getType(uri) }.getOrNull(),
            flaggedAsRecording = false,
        )
        return readTags(uri, fallbackTitle).copy(kind = kind)
    }

    private fun readTags(uri: Uri, fallbackTitle: String): Song {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Song(
                uri = uri.toString(),
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    .orIfBlank(fallbackTitle),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    .cleanArtist(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    .orEmpty(),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
                source = Song.SOURCE_IMPORTED,
            )
        } catch (e: Exception) {
            Song(
                uri = uri.toString(), title = fallbackTitle, artist = Song.UNKNOWN_ARTIST,
                album = "", durationMs = 0L, source = Song.SOURCE_IMPORTED,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun String?.orIfBlank(fallback: String) =
        if (isNullOrBlank()) fallback else trim()

    private fun String?.cleanArtist() =
        if (isNullOrBlank() || this == "<unknown>") Song.UNKNOWN_ARTIST else trim()

    private companion object {
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "amr", "mid", "midi",
        )
    }
}
