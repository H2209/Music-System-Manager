package com.musicsystemmanager.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioClassifierTest {

    private fun kind(path: String?, name: String?, mime: String? = "audio/mpeg", flagged: Boolean = false) =
        AudioClassifier.classify(path, name, mime, flagged)

    @Test fun oppoCallRecordingIsRecording() =
        assertEquals(Song.KIND_RECORDING, kind("Music/Recordings/Call Recordings/", "Mom_20241120.amr", "audio/amr-wb"))

    @Test fun soundRecorderFolderIsRecording() =
        assertEquals(Song.KIND_RECORDING, kind("Music/Recordings/", "Standard recording 3.m4a", "audio/mp4"))

    @Test fun whatsappVoiceNoteIsRecording() =
        assertEquals(Song.KIND_RECORDING, kind("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202447/", "PTT-20241120-WA0001.opus", "audio/ogg"))

    @Test fun androidFlagIsRecording() =
        assertEquals(Song.KIND_RECORDING, kind("Music/", "something.m4a", "audio/mp4", flagged = true))

    @Test fun amrAnywhereIsRecording() =
        assertEquals(Song.KIND_RECORDING, kind("Download/", "memo.amr", "audio/amr"))

    @Test fun legacyFullPathRecording() =
        assertEquals(Song.KIND_RECORDING, kind("/storage/emulated/0/Recordings/Voice/rec_001.m4a", "rec_001.m4a", "audio/mp4"))

    @Test fun importedDocumentUriRecording() =
        assertEquals(Song.KIND_RECORDING, kind("content://com.android.externalstorage.documents/document/primary:Music/Recordings/x.m4a", "x.m4a", "audio/mp4"))

    @Test fun downloadedSongIsSong() =
        assertEquals(Song.KIND_SONG, kind("Download/", "Homage.mp3"))

    @Test fun musicFolderSongIsSong() =
        assertEquals(Song.KIND_SONG, kind("Music/", "Sharada-2.mp3"))

    @Test fun songWithRecordInTitleIsSong() =
        assertEquals(Song.KIND_SONG, kind("Music/Albums/", "Record Breaker.mp3"))

    @Test fun recordsLabelFolderIsSong() =
        assertEquals(Song.KIND_SONG, kind("Music/Sony Records/", "Hit Song.flac", "audio/flac"))
}
