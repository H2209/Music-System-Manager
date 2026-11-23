package com.musicsystemmanager.app.ui.components

import android.icu.text.BreakIterator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicsystemmanager.app.AppContainer
import com.musicsystemmanager.app.data.Song
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.LocalContentColor

data class MenuAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val onClick: () -> Unit,
)

/** One song in a list: tap to play, plus visible 👍 Like, Play and Add-to-playlist buttons. */
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLike: () -> Unit,
    menu: List<MenuAction> = emptyList(),
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onPlay),
        leadingContent = {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isCurrent) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        headlineContent = {
            Text(
                song.title,
                maxLines = 2, // long names wrap instead of being cut short
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
            )
        },
        supportingContent = {
            val duration = if (song.durationMs > 0) " • ${formatDuration(song.durationMs)}" else ""
            Text(song.artist + duration, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LikeButton(liked = song.isLiked, onClick = onToggleLike, modifier = Modifier.size(40.dp))
                IconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${song.title}")
                }
                IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Add ${song.title} to a playlist",
                    )
                }
                if (menu.isNotEmpty()) OverflowMenu(menu, Modifier.size(40.dp))
            }
        },
    )
}

/** 👍 button: filled and coloured when the song is liked. */
@Composable
fun LikeButton(liked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            contentDescription = if (liked) "Unlike" else "Like",
            tint = if (liked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

/** Likes/unlikes a song and shows a short confirmation. */
fun AppContainer.toggleLike(songId: Long, showMessage: (String) -> Unit) {
    appScope.launch {
        val liked = repository.toggleLike(songId)
        showMessage(if (liked) "👍 Added to Liked songs" else "Removed from Liked songs")
    }
}

@Composable
fun OverflowMenu(
    actions: List<MenuAction>,
    modifier: Modifier = Modifier,
    contentDescription: String = "More options",
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = modifier) {
            Icon(Icons.Filled.MoreVert, contentDescription = contentDescription)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = action.icon?.let { icon -> { Icon(icon, contentDescription = null) } },
                    onClick = { open = false; action.onClick() },
                )
            }
        }
    }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
    )
}

private val QUICK_EMOJIS = listOf(
    "🎵", "🎧", "🔥", "❤️", "😎", "🌙", "☀️", "🚗", "🏋️", "🎉",
    "😢", "🧘", "☕", "🌧️", "💃", "🎸", "🎹", "🥁", "✨", "🌊",
)

/** Name a playlist anything — the keyboard's emoji key works, plus quick-pick emojis. */
@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var field by remember {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }
    // Close the keyboard with the dialog, so it doesn't linger on the next screen.
    val keyboard = LocalSoftwareKeyboardController.current
    val dismiss = { keyboard?.hide(); onDismiss() }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { if (it.text.length <= MAX_NAME_LENGTH) field = it },
                    singleLine = true,
                    label = { Text("Playlist name") },
                    placeholder = { Text("e.g. 🏋️ Gym Pump") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Add an emoji", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(QUICK_EMOJIS) { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                val start = field.selection.min
                                val end = field.selection.max
                                val text = field.text.replaceRange(start, end, emoji)
                                if (text.length <= MAX_NAME_LENGTH) {
                                    field = TextFieldValue(text, TextRange(start + emoji.length))
                                }
                            },
                        ) {
                            Text(emoji, fontSize = 22.sp, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { keyboard?.hide(); onConfirm(field.text.trim()) },
                enabled = field.text.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

private const val MAX_NAME_LENGTH = 60

/** Round badge showing the playlist's first emoji/letter, e.g. "🔥 Hits" -> 🔥. */
@Composable
fun PlaylistBadge(name: String, size: Int = 48) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            firstGrapheme(name).uppercase(),
            fontSize = (size * 0.45f).sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** First user-visible character, keeping multi-codepoint emojis (👨‍👩‍👧, 🏋️) intact. */
fun firstGrapheme(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return "♪"
    val it = BreakIterator.getCharacterInstance()
    it.setText(trimmed)
    val end = it.next()
    return if (end == BreakIterator.DONE) trimmed else trimmed.substring(0, end)
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, actions: @Composable () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, fontSize = 56.sp)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        actions()
    }
}

val ListContentPadding = PaddingValues(bottom = 16.dp)

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
