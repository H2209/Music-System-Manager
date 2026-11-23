package com.musicsystemmanager.app.data.fingerprint

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class FingerprintTest {

    /** A synthetic "song": a random melody (notes with harmonics) plus a simple beat. */
    private fun song(
        seed: Int,
        sampleRate: Int = 44_100,
        seconds: Double = 40.0,
        gain: Double = 0.5,
        noise: Double = 0.0,
        leadingSilence: Double = 0.0,
    ): Fingerprint? {
        val rnd = Random(seed)
        val notes = List(200) { 220.0 * Math.pow(2.0, rnd.nextInt(0, 24) / 12.0) }
        // Each song has its own tempo, like real music (seeds 4 and 9 share one on purpose).
        val noteLen = 0.2 + (seed % 5) * 0.04
        val beatEvery = 0.4 + (seed % 5) * 0.05
        val noiseRnd = Random(seed * 31 + 7)
        val builder = FingerprintBuilder(sampleRate)
        val total = ((seconds + leadingSilence) * sampleRate).toInt()
        for (n in 0 until total) {
            val t = n.toDouble() / sampleRate - leadingSilence
            var s = 0.0
            if (t >= 0) {
                val f = notes[(t / noteLen).toInt() % notes.size]
                val f2 = notes[((t / noteLen).toInt() + 3) % notes.size] / 2
                s = 0.6 * sin(2 * PI * f * t) + 0.25 * sin(4 * PI * f * t) + 0.3 * sin(2 * PI * f2 * t)
                val beat = (t % beatEvery) < 0.05
                if (beat) s += 0.4 * sin(2 * PI * 80 * t)
            }
            s = s * gain + noise * (noiseRnd.nextDouble() * 2 - 1)
            builder.add(s.toFloat())
        }
        return builder.finish()
    }

    /** A plain tone with a slow wobble in volume (like this app's test files). */
    private fun tone(freq: Double, seconds: Double = 40.0, sampleRate: Int = 22_050): Fingerprint? {
        val b = FingerprintBuilder(sampleRate)
        for (n in 0 until (seconds * sampleRate).toInt()) {
            val t = n.toDouble() / sampleRate
            b.add((0.25 * sin(2 * PI * freq * t) * (0.6 + 0.4 * sin(2 * PI * 2 * t))).toFloat())
        }
        return b.finish()
    }

    private fun same(a: Fingerprint?, b: Fingerprint?) = FingerprintMatcher.isSameRecording(a!!, b!!)

    /** Prints the scores behind the decisions (see the test report) for tuning. */
    @Test fun printMargins() {
        fun show(label: String, a: Fingerprint?, b: Fingerprint?) = println("MARGIN $label: similarity=%.3f profile=%.3f".format(FingerprintMatcher.similarity(a!!, b!!), FingerprintMatcher.profileSimilarity(a, b)))
        show("copy 48k quiet noisy", song(1), song(1, sampleRate = 48_000, gain = 0.2, noise = 0.02))
        show("copy 22k noisy", song(2), song(2, sampleRate = 22_050, noise = 0.05))
        show("copy +1s silence", song(3), song(3, leadingSilence = 1.0))
        for (o in 10..14) show("different song 4 vs $o", song(4), song(o))
        show("tone 330 vs 392", tone(330.0), tone(392.0))
        show("tone 440 vs 523", tone(440.0), tone(523.0))
        show("tone 440 vs 494", tone(440.0), tone(494.0))
        show("steady 400 vs 420", steadyTone(400.0), steadyTone(420.0))
        show("copy 4 vs itself at 22k", song(4), song(4, sampleRate = 22_050, gain = 0.3, noise = 0.05))
        show("SAME TEMPO different song 4 vs 9", song(4), song(9))
    }

    @Test fun identicalAudioMatches() = assertTrue(same(song(1), song(1)))

    @Test fun differentSampleRateQuieterAndNoisyStillMatches() =
        assertTrue(same(song(1), song(1, sampleRate = 48_000, gain = 0.2, noise = 0.02)))

    @Test fun lowQualityCopyMatches() =
        assertTrue(same(song(2), song(2, sampleRate = 22_050, noise = 0.05)))

    @Test fun copyWithSilenceAtStartMatches() =
        assertTrue(same(song(3), song(3, leadingSilence = 1.0)))

    @Test fun differentSongsDoNotMatch() {
        for (other in listOf(9, 10, 11, 12, 13, 14)) assertFalse("song 4 vs $other", same(song(4), song(other)))
    }

    @Test fun differentTonesWithSameRhythmDoNotMatch() {
        assertFalse(same(tone(330.0), tone(392.0)))
        assertFalse(same(tone(440.0), tone(523.0)))
    }

    @Test fun sameToneMatches() = assertTrue(same(tone(440.0), tone(440.0, sampleRate = 44_100)))

    /** Seen on the emulator: two pulsing tones only 2 semitones apart were merged. */
    @Test fun nearbyPulsingTonesDoNotMatch() {
        assertFalse(same(tone(440.0), tone(494.0)))
        assertFalse(same(tone(392.0), tone(440.0)))
    }

    /** A completely steady beep, never changing: nothing to compare, so never merged. */
    private fun steadyTone(freq: Double, seconds: Double = 20.0): Fingerprint? {
        val b = FingerprintBuilder(8_000)
        for (n in 0 until (seconds * 8_000).toInt()) b.add((0.2 * sin(2 * PI * freq * n / 8_000)).toFloat())
        return b.finish()
    }

    /** Seen on the emulator: steady test beeps 20 Hz apart were merged. */
    @Test fun steadyBeepsAreNeverMerged() {
        assertFalse(same(steadyTone(400.0), steadyTone(420.0)))
        assertFalse(same(steadyTone(600.0), steadyTone(620.0)))
    }

    @Test fun silenceHasNoFingerprint() {
        val b = FingerprintBuilder(44_100)
        repeat(44_100 * 20) { b.add(0f) }
        assertNull(b.finish())
    }

    @Test fun tooShortHasNoFingerprint() {
        val b = FingerprintBuilder(44_100)
        repeat(44_100) { b.add((sin(it / 10.0) * 0.5).toFloat()) }
        assertNull(b.finish())
    }

    @Test fun savesAndLoads() {
        val fp = song(5)
        assertNotNull(fp)
        val back = Fingerprint.fromBytes(fp!!.toBytes())!!
        assertArrayEquals(fp.envelope, back.envelope)
        assertTrue(fp.frames == back.frames)
    }
}
