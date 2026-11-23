package com.musicsystemmanager.app.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** "New version available" pop-up. Update opens the GitHub release page to download the APK. */
@Composable
fun UpdateDialog(
    update: AvailableUpdate,
    currentVersion: String,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onLater,
        icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Version ${update.version} is out. You have $currentVersion.\n" +
                        "Your songs, playlists and likes are kept when you update.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (update.notes.isNotBlank()) {
                    Text(
                        update.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                TextButton(onClick = onSkip) { Text("Skip this version") }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(update.pageUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: ActivityNotFoundException) {
                    showMessage("No browser found to open the download page")
                }
                onLater()
            }) { Text("Update") }
        },
        dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
    )
}
