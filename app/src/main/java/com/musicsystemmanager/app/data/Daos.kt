package com.musicsystemmanager.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SongDao {

    /** Empty query returns everything. Matches title, artist or album. */
    @Query(
        """
        SELECT * FROM songs
        WHERE :query = ''
           OR title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE
        """
    )
    abstract fun search(query: String): Flow<List<Song>>

    @Query("SELECT COUNT(*) FROM songs")
    abstract fun count(): Flow<Int>

    @Query("SELECT * FROM songs WHERE id = :id")
    abstract suspend fun byId(id: Long): Song?

    // ---------- Liked songs (👍) ----------

    @Query("SELECT * FROM songs WHERE likedAt IS NOT NULL ORDER BY likedAt DESC")
    abstract fun liked(): Flow<List<Song>>

    @Query("SELECT COUNT(*) FROM songs WHERE likedAt IS NOT NULL")
    abstract fun likedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs WHERE id = :id AND likedAt IS NOT NULL")
    abstract fun likedFlag(id: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs WHERE id = :id AND likedAt IS NOT NULL")
    abstract suspend fun likedFlagNow(id: Long): Int

    @Query("UPDATE songs SET likedAt = :likedAt WHERE id = :id")
    abstract suspend fun setLikedAt(id: Long, likedAt: Long?)

    /** Likes or un-likes the song. Returns true if it is now liked. */
    @Transaction
    open suspend fun toggleLike(id: Long): Boolean {
        val wasLiked = likedFlagNow(id) > 0
        setLikedAt(id, if (wasLiked) null else System.currentTimeMillis())
        return !wasLiked
    }

    /** Returns the new row id, or -1 if the song (same URI) was already in the library. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(songs: List<Song>): List<Long>

    @Query("SELECT uri FROM songs WHERE source = 'device'")
    abstract suspend fun deviceUris(): List<String>

    @Query("DELETE FROM songs WHERE uri IN (:uris)")
    abstract suspend fun deleteSongsByUri(uris: List<String>)

    @Query("DELETE FROM songs WHERE id = :id")
    abstract suspend fun deleteSong(id: Long)

    @Query("DELETE FROM playlist_songs WHERE songId NOT IN (SELECT id FROM songs)")
    abstract suspend fun deleteOrphanPlaylistEntries()

    @Transaction
    open suspend fun deleteByUris(uris: List<String>) {
        uris.chunked(500).forEach { deleteSongsByUri(it) }
        deleteOrphanPlaylistEntries()
    }

    @Transaction
    open suspend fun delete(id: Long) {
        deleteSong(id)
        deleteOrphanPlaylistEntries()
    }
}

@Dao
abstract class PlaylistDao {

    @Query(
        """
        SELECT p.*, COUNT(ps.songId) AS songCount
        FROM playlists p LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
        GROUP BY p.id
        ORDER BY p.createdAt DESC
        """
    )
    abstract fun playlistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract fun playlist(id: Long): Flow<Playlist?>

    @Insert
    abstract suspend fun insert(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun deletePlaylistRow(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    abstract suspend fun clear(id: Long)

    @Transaction
    open suspend fun delete(id: Long) {
        clear(id)
        deletePlaylistRow(id)
    }

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON ps.songId = s.id
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position
        """
    )
    abstract fun songs(playlistId: Long): Flow<List<Song>>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId")
    abstract fun songIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    abstract suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertEntry(entry: PlaylistSong): Long

    /** Adds to the end. Returns false if the song is already in that playlist. */
    @Transaction
    open suspend fun addSong(playlistId: Long, songId: Long): Boolean =
        insertEntry(PlaylistSong(playlistId, songId, maxPosition(playlistId) + 1)) != -1L

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    abstract suspend fun removeSong(playlistId: Long, songId: Long)
}
