package com.musicsystemmanager.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Song::class, Playlist::class, PlaylistSong::class],
    version = 3,
    exportSchema = false,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        /** v2 adds 👍 Liked songs. Keeps everyone's existing songs and playlists. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN likedAt INTEGER")
            }
        }

        /** v3 (app 1.0.1) separates songs from voice/call recordings. Existing rows get NULL = song
         *  until the next scan classifies them. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN kind TEXT")
            }
        }

        fun build(context: Context): MusicDatabase =
            Room.databaseBuilder(context.applicationContext, MusicDatabase::class.java, "music.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
