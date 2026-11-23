package com.musicsystemmanager.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Song::class, Playlist::class, PlaylistSong::class, SongFingerprint::class],
    version = 4,
    exportSchema = false,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun fingerprintDao(): FingerprintDao

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

        /** v4 (app 1.0.3): audio fingerprints and "copy of" links for duplicate detection. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN duplicateOf INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fingerprints` (`songId` INTEGER NOT NULL, " +
                        "`data` BLOB, `version` INTEGER NOT NULL, PRIMARY KEY(`songId`))"
                )
            }
        }

        fun build(context: Context): MusicDatabase =
            Room.databaseBuilder(context.applicationContext, MusicDatabase::class.java, "music.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
