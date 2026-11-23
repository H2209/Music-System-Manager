package com.musicsystemmanager.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicsystemmanager.app.AppContainer
import com.musicsystemmanager.app.playback.QUEUE_WINDOW
import com.musicsystemmanager.app.playback.QueueEntry
import com.musicsystemmanager.app.ui.components.EmptyState

private val ROW_HEIGHT = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(container: AppContainer, onBack: () -> Unit, showMessage: (String) -> Unit) {
    val player = container.player
    val state by player.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Queue") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (state.upNextTotal >= 2) {
                    TextButton(onClick = { player.shuffleUpNext(); showMessage("Shuffled what's up next") }) {
                        Icon(Icons.Filled.Shuffle, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle")
                    }
                }
            },
        )

        if (state.songId == null) {
            EmptyState(emoji = "🎶", title = "Nothing playing", body = "Play a song or playlist to build a queue.")
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SectionLabel("Now playing")
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                SongText(state.title, state.artist, highlight = true, modifier = Modifier.weight(1f))
            }

            SectionLabel(
                if (state.upNextTotal == 0) "Up next" else "Up next · ${state.upNextTotal}"
            )
            if (state.upNext.isEmpty()) {
                Text(
                    "Nothing else queued. Use \"Play next\" or \"Add to queue\" from any song's ⋮ menu.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                Text(
                    "Drag the ● handle to move a song up or down. Tap a song to play it now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                ReorderableQueue(
                    items = state.upNext,
                    onMove = player::moveInQueue,
                    onPlay = player::playQueueItem,
                )
                val more = state.upNextTotal - state.upNext.size
                if (more > 0) {
                    Text(
                        "+ $more more ${if (more == 1) "song" else "songs"} will join the queue as these play " +
                            "(the queue shows $QUEUE_WINDOW at a time).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Up-next list. Only the round handle on the right starts a drag, so scrolling
 * anywhere else on the list scrolls normally.
 */
@Composable
private fun ReorderableQueue(
    items: List<QueueEntry>,
    onMove: (from: Int, to: Int) -> Unit,
    onPlay: (index: Int) -> Unit,
) {
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    // Local copy so the list can rearrange live while dragging; resets when the player updates.
    var order by remember(items) { mutableStateOf(items) }
    var dragging by remember { mutableStateOf<Int?>(null) } // queue index of the dragged song
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun finishDrag() {
        val from = dragging ?: return
        val newPos = order.indexOfFirst { it.index == from }
        val oldPos = items.indexOfFirst { it.index == from }
        if (newPos >= 0 && oldPos >= 0 && newPos != oldPos) {
            onMove(from, items[newPos].index)
        }
        dragging = null
        dragOffset = 0f
    }

    Column {
        order.forEach { entry ->
            key(entry.index, entry.songId) {
                val isDragged = dragging == entry.index
                Row(
                    Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .background(
                            if (isDragged) MaterialTheme.colorScheme.surfaceContainerHighest
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { onPlay(entry.index) }
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SongText(entry.title, entry.artist, highlight = false, modifier = Modifier.weight(1f))
                    // The circle handle on the right.
                    Surface(
                        shape = CircleShape,
                        color = if (isDragged) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp)
                            .pointerInput(entry.index, entry.songId) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = entry.index
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val pos = order.indexOfFirst { it.index == dragging }
                                        if (pos < 0) return@detectDragGestures
                                        if (dragOffset > rowPx / 2 && pos < order.lastIndex) {
                                            order = order.toMutableList().apply { add(pos + 1, removeAt(pos)) }
                                            dragOffset -= rowPx
                                        } else if (dragOffset < -rowPx / 2 && pos > 0) {
                                            order = order.toMutableList().apply { add(pos - 1, removeAt(pos)) }
                                            dragOffset += rowPx
                                        }
                                    },
                                    onDragEnd = { finishDrag() },
                                    onDragCancel = { finishDrag() },
                                )
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Drag ${entry.title} up or down",
                                tint = if (isDragged) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongText(title: String, artist: String, highlight: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (highlight) FontWeight.SemiBold else null,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
