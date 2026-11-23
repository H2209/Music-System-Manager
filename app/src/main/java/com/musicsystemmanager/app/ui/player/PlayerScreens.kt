package com.musicsystemmanager.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.musicsystemmanager.app.AppContainer
import com.musicsystemmanager.app.data.Song
import com.musicsystemmanager.app.playback.PlayerConnection
import com.musicsystemmanager.app.playback.PlayerUiState
import com.musicsystemmanager.app.ui.components.AddToPlaylistSheet
import com.musicsystemmanager.app.ui.components.MenuAction
import com.musicsystemmanager.app.ui.components.OverflowMenu
import com.musicsystemmanager.app.ui.components.formatDuration
import com.musicsystemmanager.app.ui.components.SongArtwork
import com.musicsystemmanager.app.ui.components.rememberArtwork
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Mic
import com.musicsystemmanager.app.ui.components.toggleLike
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

/** Polls the player position twice a second while on screen. */
@Composable
private fun rememberPosition(player: PlayerConnection): Long {
    var position by remember { mutableLongStateOf(player.position()) }
    LaunchedEffect(player) {
        while (true) {
            position = player.position()
            delay(500)
        }
    }
    return position
}

/** Whether the current song is liked (updates live). */
@Composable
private fun rememberIsLiked(container: AppContainer, songId: Long?): Boolean {
    val flow = remember(songId) {
        if (songId == null) flowOf(false) else container.repository.isLiked(songId)
    }
    val liked by flow.collectAsStateWithLifecycle(initialValue = false)
    return liked
}

/**
 * Small player above the bottom tabs. Tap it for the full player page.
 * The ⋮ menu has previous / pause / next, queue, sleep timer and like.
 */
@Composable
fun MiniPlayer(
    container: AppContainer,
    state: PlayerUiState,
    onOpen: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenTimer: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val player = container.player
    val position = rememberPosition(player)
    val liked = rememberIsLiked(container, state.songId)
    val timerLeft = rememberTimerRemaining(state.sleepTimerEndsAt)

    Surface(tonalElevation = 3.dp) {
        Column {
            LinearProgressIndicator(
                progress = {
                    if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongArtwork(
                    uri = state.uri,
                    isRecording = false,
                    isCurrent = false,
                    size = 40.dp,
                    cornerRadius = 8.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        state.artist + if (timerLeft != null) "  ·  ⏲ ${formatDuration(timerLeft)}" else "",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = player::togglePlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                    )
                }
                OverflowMenu(
                    contentDescription = "Player options",
                    actions = listOf(
                        MenuAction("Previous song", Icons.Filled.SkipPrevious, player::previous),
                        MenuAction(
                            if (state.isPlaying) "Pause" else "Play",
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            player::togglePlayPause,
                        ),
                        MenuAction("Next song", Icons.Filled.SkipNext, player::next),
                        MenuAction("Queue", Icons.AutoMirrored.Filled.QueueMusic, onOpenQueue),
                        MenuAction(
                            if (timerLeft != null) "Sleep timer (${formatDuration(timerLeft)} left)"
                            else "Sleep timer",
                            Icons.Filled.Timer,
                            onOpenTimer,
                        ),
                        MenuAction(
                            if (liked) "Remove from Liked" else "Like this song",
                            if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        ) {
                            state.songId?.let { container.toggleLike(it, showMessage) }
                        },
                    ),
                )
            }
        }
    }
}

/** Full-screen player page. */
@Composable
fun NowPlayingScreen(
    container: AppContainer,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenTimer: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val player = container.player
    val state by player.state.collectAsStateWithLifecycle()
    val position = rememberPosition(player)
    val liked = rememberIsLiked(container, state.songId)
    val timerLeft = rememberTimerRemaining(state.sleepTimerEndsAt)
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(state.songId) {
        val id = state.songId
        currentSong = if (id != null) container.repository.song(id) else null
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close player")
            }
            Text(
                "Now playing",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenTimer) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = "Sleep timer",
                    tint = if (timerLeft != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = { showAddSheet = true }, enabled = currentSong != null) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to playlist")
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .weight(1f, fill = false)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            // The song's own cover picture, large; the gradient + note shows when it has none.
            val bigArt = rememberArtwork(state.uri, sizePx = 900)
            if (bigArt != null) {
                Image(
                    bigArt,
                    contentDescription = "Cover art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    if (currentSong?.isRecording == true) Icons.Filled.Mic else Icons.Filled.MusicNote,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(120.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            state.title.ifEmpty { "Nothing playing" },
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Text(
            state.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
        val duration = state.durationMs.coerceAtLeast(1L).toFloat()
        Slider(
            value = (dragValue ?: position.toFloat()).coerceIn(0f, duration),
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { player.seekTo(it.toLong()) }
                dragValue = null
            },
            valueRange = 0f..duration,
            enabled = state.durationMs > 0,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatDuration((dragValue?.toLong() ?: position)),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { player.shuffleUpNext(); showMessage("Shuffled what's up next") },
                enabled = state.upNextTotal >= 2,
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle up next")
            }
            IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp))
            }
            FilledIconButton(onClick = player::togglePlayPause, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = player::next, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp))
            }
            IconButton(onClick = player::cycleRepeat) {
                val repeatOn = state.repeatMode != Player.REPEAT_MODE_OFF
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        else -> "Repeat off"
                    },
                    tint = if (repeatOn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 👍 Like · ⏲ Timer · ☰ Queue
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            val likeAction = { state.songId?.let { container.toggleLike(it, showMessage) }; Unit }
            if (liked) {
                FilledTonalButton(onClick = likeAction) {
                    Icon(Icons.Filled.ThumbUp, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Liked")
                }
            } else {
                OutlinedButton(onClick = likeAction) {
                    Icon(Icons.Outlined.ThumbUp, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Like")
                }
            }
            OutlinedButton(onClick = onOpenTimer) {
                Icon(Icons.Filled.Timer, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (timerLeft != null) formatDuration(timerLeft) else "Timer")
            }
            OutlinedButton(onClick = onOpenQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Queue")
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    val song = currentSong
    if (showAddSheet && song != null) {
        AddToPlaylistSheet(container, song, onDismiss = { showAddSheet = false }, showMessage = showMessage)
    }
}
