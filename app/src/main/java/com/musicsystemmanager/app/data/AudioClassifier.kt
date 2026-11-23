package com.musicsystemmanager.app.data

/**
 * Decides whether an audio file is a song or a recording (voice memo, call recording,
 * WhatsApp/Telegram voice note). Many phones, Oppo/ColorOS included, save recordings under
 * Music/ and mark them as music, so the file's folder, name and format decide.
 */
object AudioClassifier {

    /** Folder names recorder, dialer and chat apps use. */
    private val RECORDING_FOLDER = Regex(
        """(?i)(^|[/:])(recordings?|call ?recordings?|recorder|sound ?recorder|voice ?recorder|""" +
            """voice ?notes?|voice ?memos?|voicemail|callrecord\w*|""" +
            """whatsapp (voice notes|audio)|telegram audio|audio ?notes?)(/|$)"""
    )

    /** File names typical of recordings, e.g. "Recording_0012.m4a", "PTT-20240101-WA0003.opus". */
    private val RECORDING_NAME = Regex(
        """(?i)^(rec[_ -]|recording|record_|voice[_ -]|call[_ -]|callrec|ptt-|aud-\d|""" +
            """new recording|standard recording)"""
    )

    /** Speech codecs recorders use. Music is basically never stored in these. */
    private val RECORDING_MIME = setOf("audio/amr", "audio/amr-wb", "audio/3gpp", "audio/3gpp2")
    private val RECORDING_EXT = setOf("amr", "awb", "3gp", "3ga", "3gpp")

    fun classify(path: String?, fileName: String?, mimeType: String?, flaggedAsRecording: Boolean): String {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        val isRecording = flaggedAsRecording ||
            (path != null && RECORDING_FOLDER.containsMatchIn(path)) ||
            (fileName != null && RECORDING_NAME.containsMatchIn(fileName)) ||
            mimeType?.lowercase() in RECORDING_MIME ||
            ext in RECORDING_EXT
        return if (isRecording) Song.KIND_RECORDING else Song.KIND_SONG
    }
}
