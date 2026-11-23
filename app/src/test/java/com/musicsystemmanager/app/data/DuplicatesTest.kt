package com.musicsystemmanager.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicatesTest {

    private var nextId = 1L
    private fun song(
        title: String,
        artist: String = "Lady Gaga",
        durationMs: Long = 252_000,
        source: String = Song.SOURCE_DEVICE,
        liked: Boolean = false,
    ) = Song(
        id = nextId++, uri = "content://x/${nextId}", title = title, artist = artist, album = "",
        durationMs = durationMs, source = source, likedAt = if (liked) 1L else null,
    )

    @Test fun downloadedTwiceShowsOnce() {
        val a = song("Die With A Smile")
        val b = song("Die With A Smile (1)")
        assertEquals(listOf(a), Duplicates.hide(listOf(a, b)))
    }

    @Test fun copyMarkersAreIgnored() {
        listOf("Homage - Copy", "Homage copy", "Homage [2]", "Homage (12)", "homage  ", "Homage_copy (2)")
            .forEach { assertEquals("homage", Duplicates.normalizeTitle(it)) }
    }

    @Test fun sameFileScannedAndImportedShowsOnce() {
        val imported = song("Homage", "Mild High Club", 177_720, source = Song.SOURCE_IMPORTED)
        val scanned = song("Homage", "Mild High Club", 177_700)
        assertEquals(listOf(scanned), Duplicates.hide(listOf(imported, scanned)))
    }

    @Test fun likedCopyIsTheOneKept() {
        val a = song("Song")
        val b = song("Song (1)", liked = true)
        assertEquals(listOf(b), Duplicates.hide(listOf(a, b)))
    }

    @Test fun differentLengthsAreDifferentSongs() {
        val studio = song("Live Forever", durationMs = 240_000)
        val live = song("Live Forever", durationMs = 330_000)
        assertEquals(2, Duplicates.hide(listOf(studio, live)).size)
    }

    @Test fun differentArtistsAreDifferentSongs() {
        val a = song("Hello", artist = "Adele")
        val b = song("Hello", artist = "Lionel Richie")
        assertEquals(2, Duplicates.hide(listOf(a, b)).size)
    }

    @Test fun numberInRealTitleIsKept() {
        assertEquals("track 01", Duplicates.normalizeTitle("Track 01"))
        assertEquals("1999", Duplicates.normalizeTitle("1999"))
    }

    @Test fun orderIsKept() {
        val a = song("A"); val b = song("B"); val b2 = song("B (1)"); val c = song("C")
        assertEquals(listOf(a, b, c), Duplicates.hide(listOf(a, b, b2, c)))
    }
}
