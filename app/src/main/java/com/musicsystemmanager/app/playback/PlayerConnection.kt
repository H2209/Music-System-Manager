package com.musicsystemmanager.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.musicsystemmanager.app.data.Song
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How many upcoming songs the queue screen shows at once. More join as songs finish. */
const val QUEUE_WINDOW = 15

/** One upcoming song. [index] is its position in the player's full queue. */
data class QueueEntry(
    val index: Int,
    val songId: Long?,
    val title: String,
    val artist: String,
)

data class PlayerUiState(
    val songId: Long? = null,
    /** File location of the current song (used to show its cover art). */
    val uri: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    /** Next songs, at most [QUEUE_WINDOW]. */
    val upNext: List<QueueEntry> = emptyList(),
    /** How many songs are after the current one in total (may be more than [upNext]). */
    val upNextTotal: Int = 0,
    /** Wall-clock ms when the sleep timer pauses playback, or null if no timer. */
    val sleepTimerEndsAt: Long? = null,
)

/** The UI's handle on [PlaybackService]. All calls are safe before the connection is ready. */
class PlayerConnection(context: Context) {

    private val context = context.applicationContext
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors

    private var controller: MediaController? = null
    private var pendingConnection: ListenableFuture<MediaController>? = null
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()

        override fun onPlayerError(error: PlaybackException) {
            _errors.tryEmit("Couldn't play this song. The file may have been moved or deleted.")
        }
    }

    private val controllerListener = object : MediaController.Listener {
        // The service publishes the sleep timer through session extras.
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()
    }

    fun connect() {
        if (controller != null || pendingConnection != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        pendingConnection = future
        future.addListener({
            pendingConnection = null
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(playerListener)
            refresh()
            pendingActions.forEach { it(c) }
            pendingActions.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else {
            pendingActions += action
            connect()
        }
    }

    // ---------- Playing ----------

    /** Plays [songs] as the queue, starting at [startIndex]. Shuffle mixes the order first. */
    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val ordered = if (shuffle) songs.shuffled() else songs
        val start = if (shuffle) 0 else startIndex.coerceIn(songs.indices)
        withController { c ->
            c.shuffleModeEnabled = false // the queue order on screen is the real play order
            c.setMediaItems(ordered.map { it.toMediaItem() }, start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Current position in ms (poll this from the UI for the progress bar). */
    fun position(): Long = controller?.currentPosition ?: 0L

    // ---------- Queue ----------

    fun playNext(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) return@withController playSongs(listOf(song))
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) return@withController playSongs(listOf(song))
        c.addMediaItem(song.toMediaItem())
    }

    /** Moves a queue song from one position to another (full-queue indexes). */
    fun moveInQueue(from: Int, to: Int) = withController { c ->
        if (from == to || from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount) {
            return@withController
        }
        c.moveMediaItem(from, to)
    }

    fun removeFromQueue(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount && index != c.currentMediaItemIndex) {
            c.removeMediaItem(index)
        }
    }

    /** Jumps straight to a song in the queue. */
    fun playQueueItem(index: Int) = withController { c ->
        if (index !in 0 until c.mediaItemCount) return@withController
        c.seekToDefaultPosition(index)
        c.play()
    }

    /** Mixes up the songs after the current one. */
    fun shuffleUpNext() = withController { c ->
        val start = c.currentMediaItemIndex + 1
        val count = c.mediaItemCount
        if (count - start < 2) return@withController
        val shuffled = (start until count).map { c.getMediaItemAt(it) }.shuffled()
        c.removeMediaItems(start, count)
        c.addMediaItems(shuffled)
    }

    // ---------- Sleep timer ----------

    fun setSleepTimer(minutes: Int) = withController { c ->
        c.sendCustomCommand(
            SessionCommand(PlaybackCommands.SET_SLEEP_TIMER, Bundle.EMPTY),
            Bundle().apply { putInt(PlaybackCommands.ARG_MINUTES, minutes) },
        )
    }

    fun cancelSleepTimer() = withController { c ->
        c.sendCustomCommand(
            SessionCommand(PlaybackCommands.CANCEL_SLEEP_TIMER, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    @OptIn(UnstableApi::class) // sessionExtras carries the sleep timer
    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val count = c.mediaItemCount
        val current = if (count == 0) -1 else c.currentMediaItemIndex
        val firstNext = current + 1
        val upNext = if (count == 0) emptyList() else
            (firstNext until minOf(count, firstNext + QUEUE_WINDOW)).map { i ->
                val m = c.getMediaItemAt(i)
                QueueEntry(
                    index = i,
                    songId = m.mediaId.toLongOrNull(),
                    title = m.mediaMetadata.title?.toString().orEmpty(),
                    artist = m.mediaMetadata.artist?.toString().orEmpty(),
                )
            }
        val timerEndsAt = c.sessionExtras
            .getLong(PlaybackCommands.EXTRA_SLEEP_TIMER_ENDS_AT, 0L)
            .takeIf { it > System.currentTimeMillis() }

        _state.value = PlayerUiState(
            songId = item?.mediaId?.toLongOrNull(),
            uri = (item?.requestMetadata?.mediaUri ?: item?.localConfiguration?.uri)?.toString(),
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            isPlaying = c.isPlaying ||
                (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING),
            durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
            repeatMode = c.repeatMode,
            hasNext = c.hasNextMediaItem(),
            upNext = upNext,
            upNextTotal = (count - firstNext).coerceAtLeast(0),
            sleepTimerEndsAt = timerEndsAt,
        )
    }
}

private fun Song.toMediaItem(): MediaItem {
    val uri = Uri.parse(uri)
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}
