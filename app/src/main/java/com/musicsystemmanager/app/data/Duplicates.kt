package com.musicsystemmanager.app.data

import kotlin.math.abs

/**
 * Hides extra copies of the same song in the library lists. Files on the phone are never
 * touched. Two entries count as the same song when:
 *  - the titles match, ignoring case, extra spaces, and copy markers such as "(1)",
 *    "[2]", "- Copy" or "copy 2" that phones and browsers add to repeated downloads,
 *  - the artists match, and
 *  - the lengths are within 2 seconds (or one of them is unknown).
 * This also covers one file added twice (by "Scan phone" and by "Import files").
 */
object Duplicates {

    private const val LENGTH_TOLERANCE_MS = 2_000L

    private val COPY_MARKER = Regex(
        """(?i)(\s*[\(\[]\d{1,3}[\)\]]|\s*[-_]?\s*copy(\s*\(?\d{1,3}\)?)?)+$"""
    )
    private val SPACES = Regex("""\s+""")

    fun normalizeTitle(title: String): String =
        title.trim().replace(COPY_MARKER, "").replace(SPACES, " ").trim().lowercase()

    private fun normalizeArtist(artist: String): String =
        artist.trim().replace(SPACES, " ").lowercase()

    /** Keeps the input order; from each group of copies keeps the best one. */
    fun hide(songs: List<Song>): List<Song> {
        if (songs.size < 2) return songs
        val groups = songs.groupBy { normalizeTitle(it.title) + "\u0000" + normalizeArtist(it.artist) }
        val hidden = HashSet<Long>()
        for (group in groups.values) {
            if (group.size < 2) continue
            // Within same title+artist, cluster by similar length.
            val remaining = group.toMutableList()
            while (remaining.isNotEmpty()) {
                val first = remaining.removeAt(0)
                val cluster = mutableListOf(first)
                remaining.removeAll { other ->
                    val same = sameLength(first, other)
                    if (same) cluster += other
                    same
                }
                if (cluster.size > 1) {
                    val keep = cluster.minWith(KEEP_ORDER)
                    cluster.forEach { if (it.id != keep.id) hidden += it.id }
                }
            }
        }
        return if (hidden.isEmpty()) songs else songs.filter { it.id !in hidden }
    }

    private fun sameLength(a: Song, b: Song): Boolean =
        a.durationMs <= 0 || b.durationMs <= 0 || abs(a.durationMs - b.durationMs) <= LENGTH_TOLERANCE_MS

    /** Which copy to show: a liked one, then one found by phone scan, then the oldest. */
    private val KEEP_ORDER = compareBy<Song>(
        { if (it.isLiked) 0 else 1 },
        { if (it.source == Song.SOURCE_DEVICE) 0 else 1 },
        { it.id },
    )
}
