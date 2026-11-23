package com.musicsystemmanager.app.data.fingerprint

import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An audio fingerprint: a compact description of how a recording *sounds* from start to
 * end, independent of file name, format, bitrate or volume.
 *
 * Every 100 ms the sound is split into [BANDS] pitch ranges (200 Hz – 4 kHz, where melody,
 * chords and vocals live) and the loudness of each range is stored ([envelope], one byte
 * per range per 100 ms). Two copies of the same recording have loudness curves that rise
 * and fall together all the way through; two different songs do not.
 */
class Fingerprint(val frames: Int, val envelope: ByteArray) {

    init {
        require(envelope.size == frames * BANDS)
    }

    fun level(frame: Int, band: Int): Int = envelope[frame * BANDS + band].toInt() and 0xFF

    fun toBytes(): ByteArray =
        ByteBuffer.allocate(8 + envelope.size).putInt(VERSION).putInt(frames).put(envelope).array()

    companion object {
        /** Bump when the algorithm changes, so stored fingerprints are recomputed. */
        const val VERSION = 3
        const val BANDS = 33 // ~1.6 semitones each, so nearby notes are told apart

        fun fromBytes(bytes: ByteArray): Fingerprint? = runCatching {
            val buf = ByteBuffer.wrap(bytes)
            if (buf.int != VERSION) return null
            val frames = buf.int
            val env = ByteArray(frames * BANDS).also { buf.get(it) }
            Fingerprint(frames, env)
        }.getOrNull()
    }
}

/** Decides whether two fingerprints are the same recording. */
object FingerprintMatcher {

    /**
     * How closely the two loudness curves must move together (1.0 = identical) to count as
     * the same recording. Copies of one recording score about 0.9+; different songs score
     * far lower, even in the same key and tempo.
     */
    const val MIN_CORRELATION = 0.75

    /** Allow the two files to start up to 2 s apart (e.g. a little silence added). */
    private const val MAX_SHIFT_FRAMES = 20

    /** The overall sound "colour" (average loudness of each pitch range) must also match. */
    const val MIN_PROFILE_SIMILARITY = 0.80

    /** Sounds more than 30 dB below the loudest at that moment are inaudible detail: ignored. */
    private const val AUDIBLE_RANGE_STEPS = 60 // 0.5 dB per step

    /** A song must change over time to be comparable: steady tones or noise are skipped. */
    private const val MIN_VARIATION_STEPS = 3.0 // typical swing of 1.5 dB per pitch range

    fun isSameRecording(a: Fingerprint, b: Fingerprint): Boolean {
        val pa = Prepared(a)
        val pb = Prepared(b)
        return profileSimilarity(pa, pb) >= MIN_PROFILE_SIMILARITY && similarity(pa, pb) >= MIN_CORRELATION
    }

    fun similarity(a: Fingerprint, b: Fingerprint): Double = similarity(Prepared(a), Prepared(b))
    fun profileSimilarity(a: Fingerprint, b: Fingerprint): Double = profileSimilarity(Prepared(a), Prepared(b))

    /** Levels with the inaudible floor flattened, plus each band's average. */
    private class Prepared(fp: Fingerprint) {
        val frames = fp.frames
        val levels = IntArray(fp.frames * Fingerprint.BANDS)
        val bandMean = DoubleArray(Fingerprint.BANDS)

        init {
            for (f in 0 until frames) {
                var loudest = 0
                for (b in 0 until Fingerprint.BANDS) loudest = max(loudest, fp.level(f, b))
                val floor = loudest - AUDIBLE_RANGE_STEPS
                for (b in 0 until Fingerprint.BANDS) {
                    val v = max(fp.level(f, b), floor)
                    levels[f * Fingerprint.BANDS + b] = v
                    bandMean[b] += v.toDouble()
                }
            }
            for (b in 0 until Fingerprint.BANDS) bandMean[b] /= max(1, frames)
        }

        fun at(f: Int, b: Int) = levels[f * Fingerprint.BANDS + b]
    }

    private fun profileSimilarity(a: Prepared, b: Prepared): Double {
        val ma = a.bandMean.average()
        val mb = b.bandMean.average()
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in 0 until Fingerprint.BANDS) {
            val x = a.bandMean[i] - ma
            val y = b.bandMean[i] - mb
            dot += x * y; na += x * x; nb += y * y
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / sqrt(na * nb)
    }

    /**
     * Best correlation (−1..1) between the two loudness curves over small time shifts.
     * The two must overlap for most of their length, so a song never matches a snippet.
     */
    private fun similarity(a: Prepared, b: Prepared): Double {
        val shorter = min(a.frames, b.frames)
        val longer = max(a.frames, b.frames)
        if (shorter < 20 || shorter < longer * 0.85) return 0.0
        val minOverlap = (shorter * 0.85).toInt()
        var best = -1.0
        for (shift in -MAX_SHIFT_FRAMES..MAX_SHIFT_FRAMES) {
            val from = max(0, -shift)
            val to = min(a.frames, b.frames - shift)
            if (to - from < minOverlap) continue
            var sxy = 0.0; var sxx = 0.0; var syy = 0.0
            for (f in from until to) {
                for (band in 0 until Fingerprint.BANDS) {
                    val x = a.at(f, band) - a.bandMean[band]
                    val y = b.at(f + shift, band) - b.bandMean[band]
                    sxy += x * y; sxx += x * x; syy += y * y
                }
            }
            val cells = (to - from).toDouble() * Fingerprint.BANDS
            val varied = sqrt(sxx / cells) >= MIN_VARIATION_STEPS && sqrt(syy / cells) >= MIN_VARIATION_STEPS
            if (varied) best = max(best, sxy / sqrt(sxx * syy))
        }
        return best
    }
}

/**
 * Turns a stream of mono samples (any sample rate) into a [Fingerprint].
 * Feed samples with [add], then call [finish]. Pure Kotlin so it can be unit-tested.
 */
class FingerprintBuilder(inputSampleRate: Int) {

    // Everything is first converted to one fixed rate, so a 44.1 kHz file and a 22 kHz or
    // 48 kHz copy of it are measured exactly the same way.
    private val step = inputSampleRate.toDouble() / RATE
    private var position = 0.0
    private var accSum = 0.0
    private var accCount = 0

    private val window = FloatArray(FFT_SIZE)
    private var filled = 0
    private var sinceLastFrame = 0
    private val hop = RATE / 10 // a frame every 100 ms

    private val bandBins: List<IntRange> = run {
        val edges = DoubleArray(Fingerprint.BANDS + 1) { i ->
            LOW_HZ * (HIGH_HZ / LOW_HZ).pow(i.toDouble() / Fingerprint.BANDS)
        }
        (0 until Fingerprint.BANDS).map { b ->
            val lo = (edges[b] * FFT_SIZE / RATE).toInt().coerceIn(1, FFT_SIZE / 2 - 1)
            val hi = (edges[b + 1] * FFT_SIZE / RATE).toInt().coerceIn(lo, FFT_SIZE / 2 - 1)
            lo..hi
        }
    }

    private val envelope = java.io.ByteArrayOutputStream(64 * 1024)
    private var frames = 0
    private var sumSquares = 0.0
    private var sampleCount = 0L
    private val re = DoubleArray(FFT_SIZE)
    private val im = DoubleArray(FFT_SIZE)

    /** Stop after this much audio; enough to tell songs apart, keeps long files quick. */
    private val maxSamples = RATE.toLong() * MAX_SECONDS

    val isFull: Boolean get() = sampleCount >= maxSamples

    /** One input sample at the original rate. Averages groups of them down to [RATE]. */
    fun add(sample: Float) {
        if (isFull) return
        accSum += sample
        accCount++
        position += 1.0
        if (position >= step) {
            position -= step
            addResampled((accSum / accCount).toFloat())
            accSum = 0.0
            accCount = 0
        }
    }

    private fun addResampled(sample: Float) {
        sampleCount++
        sumSquares += sample * sample
        // Ring buffer of the last FFT_SIZE samples.
        window[filled % FFT_SIZE] = sample
        filled++
        sinceLastFrame++
        if (filled >= FFT_SIZE && sinceLastFrame >= hop) {
            sinceLastFrame = 0
            analyseFrame()
        }
    }

    /** null for silent or too-short audio, which can't be compared reliably. */
    fun finish(): Fingerprint? {
        if (frames < 20 || sampleCount == 0L) return null
        val rms = sqrt(sumSquares / sampleCount)
        if (rms < SILENCE_RMS) return null
        return Fingerprint(frames, envelope.toByteArray())
    }

    private fun analyseFrame() {
        val start = filled % FFT_SIZE // oldest sample in the ring
        for (i in 0 until FFT_SIZE) {
            re[i] = window[(start + i) % FFT_SIZE] * HANN[i]
            im[i] = 0.0
        }
        fft(re, im)
        for (b in 0 until Fingerprint.BANDS) {
            var e = 0.0
            for (k in bandBins[b]) e += re[k] * re[k] + im[k] * im[k]
            // Loudness in decibel-like steps; quiet floor at 0, ~0.5 dB per step.
            val level = (ln(e + 1e-10) * 4.34 * 2 + FLOOR).roundToInt().coerceIn(0, 255)
            envelope.write(level)
        }
        frames++
    }

    companion object {
        /** Internal analysis rate (Hz): plenty for the 200 Hz – 4 kHz range used. */
        const val RATE = 11_025
        const val FFT_SIZE = 2048 // ~186 ms window
        private const val LOW_HZ = 200.0
        private const val HIGH_HZ = 4000.0
        private const val MAX_SECONDS = 10 * 60L
        private const val SILENCE_RMS = 0.003 // about -50 dB
        private const val FLOOR = 160.0 // shifts levels so near-silence maps to ~0

        private val HANN = DoubleArray(FFT_SIZE) { 0.5 - 0.5 * cos(2 * PI * it / (FFT_SIZE - 1)) }
        private val COS = DoubleArray(FFT_SIZE / 2) { cos(-2 * PI * it / FFT_SIZE) }
        private val SIN = DoubleArray(FFT_SIZE / 2) { sin(-2 * PI * it / FFT_SIZE) }

        /** In-place radix-2 FFT for FFT_SIZE points. */
        private fun fft(re: DoubleArray, im: DoubleArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var len = 2
            while (len <= n) {
                val step = n / len
                for (i in 0 until n step len) {
                    for (k in 0 until len / 2) {
                        val c = COS[k * step]; val s = SIN[k * step]
                        val a = i + k; val b = a + len / 2
                        val xr = re[b] * c - im[b] * s
                        val xi = re[b] * s + im[b] * c
                        re[b] = re[a] - xr; im[b] = im[a] - xi
                        re[a] += xr; im[a] += xi
                    }
                }
                len = len shl 1
            }
        }
    }
}
