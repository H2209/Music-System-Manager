package com.musicsystemmanager.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicsystemmanager.app.data.ArtworkLoader

/** Provided once in AppRoot so any song row can show cover art. */
val LocalArtworkLoader = staticCompositionLocalOf<ArtworkLoader?> { null }

/** The cover picture embedded in the file at [uri], or null while loading / if it has none. */
@Composable
fun rememberArtwork(uri: String?, sizePx: Int): ImageBitmap? {
    val loader = LocalArtworkLoader.current
    var art by remember(uri, sizePx) {
        mutableStateOf(if (uri != null) loader?.cached(uri, sizePx)?.asImageBitmap() else null)
    }
    LaunchedEffect(uri, sizePx, loader) {
        val bitmap = if (uri != null && loader != null) loader.load(uri, sizePx) else null
        art = bitmap?.asImageBitmap()
    }
    return art
}

/**
 * Square thumbnail on the left of a song: its cover art if it has one, a 🎤 mic badge for
 * recordings, otherwise a music note. The playing song gets an equalizer overlay.
 */
@Composable
fun SongArtwork(
    uri: String?,
    isRecording: Boolean,
    isCurrent: Boolean,
    size: Dp = 44.dp,
    cornerRadius: Dp = 10.dp,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    // Recordings never carry cover art, so don't bother reading the file.
    val art = rememberArtwork(if (isRecording) null else uri, sizePx)
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (isRecording) colors.tertiaryContainer else colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (isCurrent) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                Icon(Icons.Filled.GraphicEq, null, tint = Color.White)
            }
        } else {
            Icon(
                when {
                    isCurrent -> Icons.Filled.GraphicEq
                    isRecording -> Icons.Filled.Mic
                    else -> Icons.Filled.MusicNote
                },
                contentDescription = if (isRecording) "Recording" else null,
                tint = if (isRecording) colors.onTertiaryContainer else colors.onPrimaryContainer,
            )
        }
    }
}
