package com.musicsystemmanager.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "songs", indices = [Index(value = ["uri"], unique = true)])
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** content:// URI used to play the file. */
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** [SOURCE_DEVICE] = found by scanning the phone, [SOURCE_IMPORTED] = picked by the user. */
    val source: String,
    val addedAt: Long = System.currentTimeMillis(),
    /** When the user tapped 👍, or null if not liked. */
    val likedAt: Long? = null,
    /** [KIND_RECORDING] for voice/call recordings; null or [KIND_SONG] for music. */
    val kind: String? = KIND_SONG,
    /** If this file is an exact copy of another song (same audio), that song's id. Hidden. */
    val duplicateOf: Long? = null,
) {
    val isLiked: Boolean get() = likedAt != null
    val isRecording: Boolean get() = kind == KIND_RECORDING

    companion object {
        const val KIND_SONG = "song"
        const val KIND_RECORDING = "recording"
        const val SOURCE_DEVICE = "device"
        const val SOURCE_IMPORTED = "imported"
        const val UNKNOWN_ARTIST = "Unknown artist"
    }
}

/** Playlist names are plain Unicode text, so emojis work out of the box. */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId")],
)
data class PlaylistSong(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * Saved audio fingerprint of a song, so each file is only "listened to" once.
 * [data] is null when the file couldn't be analysed (silent, unreadable).
 */
@Entity(tableName = "fingerprints")
class SongFingerprint(
    @PrimaryKey val songId: Long,
    val data: ByteArray?,
    val version: Int,
)

data class PlaylistWithCount(
    @Embedded val playlist: Playlist,
    val songCount: Int,
)
