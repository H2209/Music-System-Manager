package com.musicsystemmanager.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test fun newerPatch() = assertTrue(UpdateChecker.isNewer("1.0.2", "1.0.1"))
    @Test fun newerMinor() = assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.9"))
    @Test fun twoDigitParts() = assertTrue(UpdateChecker.isNewer("1.0.10", "1.0.9"))
    @Test fun vPrefix() = assertTrue(UpdateChecker.isNewer("v1.0.2", "1.0.1"))
    @Test fun sameIsNotNewer() = assertFalse(UpdateChecker.isNewer("1.0.1", "1.0.1"))
    @Test fun olderIsNotNewer() = assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.1"))
    @Test fun missingPartCountsAsZero() = assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"))

    @Test fun notesLoseMarkdown() {
        val notes = UpdateChecker.cleanNotes("## What's new\n- **Cover art** for songs\n- Play turns into Pause")
        assertEquals("What's new\n• Cover art for songs\n• Play turns into Pause", notes)
    }

    @Test fun notesLoseItalicsButKeepSnakeCase() {
        val notes = UpdateChecker.cleanNotes("use ⋮ → *This is a song* / *This is a recording*, file my_song_1")
        assertEquals("use ⋮ → This is a song / This is a recording, file my_song_1", notes)
    }
}
