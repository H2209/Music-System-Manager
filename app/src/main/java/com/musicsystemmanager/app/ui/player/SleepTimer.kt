package com.musicsystemmanager.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicsystemmanager.app.playback.PlayerConnection
import com.musicsystemmanager.app.ui.components.formatDuration
import kotlinx.coroutines.delay

/** Timer choices, in minutes. */
val SLEEP_TIMER_OPTIONS = listOf(5, 10, 15, 30, 45, 50, 60)

fun sleepTimerLabel(minutes: Int) = if (minutes == 60) "1 hour" else "$minutes minutes"

/** Time left on the sleep timer, ticking every second; null when no timer is set. */
@Composable
fun rememberTimerRemaining(endsAt: Long?): Long? {
    if (endsAt == null) return null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return (endsAt - now).coerceAtLeast(0L)
}

/** Bottom sheet: pick how long music keeps playing before it pauses by itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    player: PlayerConnection,
    endsAt: Long?,
    onDismiss: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val remaining = rememberTimerRemaining(endsAt)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Sleep timer",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                if (remaining != null) "Music pauses in ${formatDuration(remaining)}"
                else "Music keeps playing for the time you pick, then pauses.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (remaining != null) {
                ListItem(
                    modifier = Modifier.clickable {
                        player.cancelSleepTimer()
                        showMessage("Sleep timer off")
                        onDismiss()
                    },
                    leadingContent = { Icon(Icons.Filled.TimerOff, null) },
                    headlineContent = { Text("Turn off timer") },
                )
            }
            SLEEP_TIMER_OPTIONS.forEach { minutes ->
                ListItem(
                    modifier = Modifier.clickable {
                        player.setSleepTimer(minutes)
                        showMessage("⏲ Music will pause in ${sleepTimerLabel(minutes)}")
                        onDismiss()
                    },
                    leadingContent = { Icon(Icons.Filled.Timer, null) },
                    headlineContent = { Text(sleepTimerLabel(minutes)) },
                )
            }
        }
    }
}
