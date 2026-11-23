package com.musicsystemmanager.app.data.fingerprint

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.nio.ByteOrder

/**
 * "Listens" to a song: decodes the file (MP3, M4A, FLAC, WAV, OGG… whatever the phone can
 * play) with Android's own decoder, mixes it to mono and feeds it to [FingerprintBuilder].
 * Returns null if the file can't be read, is silent, or takes too long.
 */
object AudioDecoder {

    private const val TIMEOUT_US = 10_000L
    private const val MAX_TIME_MS = 90_000L

    fun fingerprint(context: Context, uri: Uri): Fingerprint? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            var channels = format.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var sampleRate = format.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var floatPcm = false
            var builder: FingerprintBuilder? = null
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val deadline = SystemClock.elapsedRealtime() + MAX_TIME_MS

            while (true) {
                if (SystemClock.elapsedRealtime() > deadline) return null
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex) ?: return null
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || builder?.isFull == true) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        channels = out.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        sampleRate = out.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        floatPcm = out.getIntegerOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) ==
                            AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        val b = builder ?: FingerprintBuilder(sampleRate).also { builder = it }
                        val out = codec.getOutputBuffer(outIndex)
                        if (out != null && info.size > 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            out.order(ByteOrder.LITTLE_ENDIAN)
                            val ch = channels.coerceAtLeast(1)
                            if (floatPcm) {
                                val fb = out.asFloatBuffer()
                                repeat(fb.remaining() / ch) {
                                    var s = 0f
                                    repeat(ch) { s += fb.get() }
                                    b.add(s / ch)
                                }
                            } else {
                                val sb = out.asShortBuffer()
                                repeat(sb.remaining() / ch) {
                                    var s = 0
                                    repeat(ch) { s += sb.get() }
                                    b.add(s / (ch * 32_768f))
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            return builder?.finish()
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.getIntegerOr(key: String, fallback: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback
}
