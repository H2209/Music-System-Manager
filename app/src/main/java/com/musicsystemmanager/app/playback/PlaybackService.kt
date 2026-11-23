package com.musicsystemmanager.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.musicsystemmanager.app.MainActivity

/** Custom commands the app sends to the service. */
object PlaybackCommands {
    const val SET_SLEEP_TIMER = "msm.SET_SLEEP_TIMER"
    const val CANCEL_SLEEP_TIMER = "msm.CANCEL_SLEEP_TIMER"
    const val ARG_MINUTES = "minutes"

    /** Session extra: wall-clock time (ms) when the sleep timer pauses playback. */
    const val EXTRA_SLEEP_TIMER_ENDS_AT = "sleepTimerEndsAt"
}

/**
 * Owns the actual player. Runs as a foreground service while music plays, so playback
 * continues with the screen off or the app closed. Media3 shows the notification and
 * lock-screen controls. Also runs the sleep timer.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val sleepTimerFired = Runnable {
        session?.player?.pause()
        clearSleepTimer()
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL) // keep the CPU awake while playing with the screen off
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    private inner class SessionCallback : MediaSession.Callback {

        @OptIn(UnstableApi::class) // session commands builder + accepted-result builder
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(PlaybackCommands.SET_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackCommands.SET_SLEEP_TIMER ->
                    startSleepTimer(args.getInt(PlaybackCommands.ARG_MINUTES))
                PlaybackCommands.CANCEL_SLEEP_TIMER -> clearSleepTimer()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        // Items sent from the UI carry their file URI in requestMetadata;
        // turn that back into a playable item.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
                if (uri != null) item.buildUpon().setUri(uri).build() else item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    private fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) return clearSleepTimer()
        val durationMs = minutes * 60_000L
        handler.removeCallbacks(sleepTimerFired)
        handler.postDelayed(sleepTimerFired, durationMs)
        session?.setSessionExtras(Bundle().apply {
            putLong(PlaybackCommands.EXTRA_SLEEP_TIMER_ENDS_AT, System.currentTimeMillis() + durationMs)
        })
    }

    private fun clearSleepTimer() {
        handler.removeCallbacks(sleepTimerFired)
        session?.setSessionExtras(Bundle.EMPTY)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away while paused -> stop completely. If playing, keep going.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(sleepTimerFired)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
