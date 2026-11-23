package com.musicsystemmanager.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Loads the cover picture ("banner") embedded in a song file, e.g. the album art that
 * comes with downloaded MP3s. Results are cached in memory; songs without art are
 * remembered too so they are not checked again.
 */
class ArtworkLoader(context: Context) {

    private val context = context.applicationContext

    private val cache = object : LruCache<String, Bitmap>(
        // Use up to 1/8 of the app's memory for pictures (in KB).
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val noArt = Collections.synchronizedSet(HashSet<String>())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(3)

    fun cached(uri: String, sizePx: Int): Bitmap? = cache.get(key(uri, sizePx))

    suspend fun load(uri: String, sizePx: Int): Bitmap? {
        val key = key(uri, sizePx)
        cache.get(key)?.let { return it }
        if (key in noArt) return null
        val bitmap = withContext(io) {
            runCatching { fromSystemThumbnail(uri, sizePx) ?: fromFileTags(uri, sizePx) }.getOrNull()
        }
        if (bitmap != null) cache.put(key, bitmap) else noArt += key
        return bitmap
    }

    private fun key(uri: String, sizePx: Int) = "$uri@$sizePx"

    /** Android 10+: the system's cached thumbnail of the file's cover. Fast. */
    private fun fromSystemThumbnail(uri: String, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            context.contentResolver.loadThumbnail(Uri.parse(uri), Size(sizePx, sizePx), null)
        }.getOrNull()
    }

    /** Any Android version: read the picture straight from the file's tags. */
    private fun fromFileTags(uri: String, sizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) {
                sample *= 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
