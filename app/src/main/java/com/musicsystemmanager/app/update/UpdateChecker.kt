package com.musicsystemmanager.app.update

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A newer version published on GitHub. */
data class AvailableUpdate(
    val version: String,
    val notes: String,
    /** The GitHub release page, where the APK is downloaded. */
    val pageUrl: String,
)

/**
 * Checks GitHub for a newer release of the app. It only asks GitHub for the latest
 * release's version number and notes; nothing about the user or their music is sent.
 * Automatic checks run at most once a day and can be turned off.
 */
class UpdateChecker(context: Context, private val scope: CoroutineScope) {

    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    val currentVersion: String = runCatching {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageInfo(context.packageName, 0)
        }
        info.versionName
    }.getOrNull() ?: "0"

    private val _available = MutableStateFlow<AvailableUpdate?>(null)
    /** Set when the "new version available" pop-up should show. */
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    private val _autoCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO, true))
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    fun setAutoCheck(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO, enabled).apply()
        _autoCheck.value = enabled
    }

    /** Called when the app opens. Quiet: shows nothing unless there is an update. */
    fun checkOnStartup() {
        if (!_autoCheck.value) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
        scope.launch {
            val result = fetchLatest()
            if (result != null) prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
            if (result != null && isNewer(result.version, currentVersion) &&
                result.version != prefs.getString(KEY_SKIPPED, null)
            ) {
                _available.value = result
            }
        }
    }

    /** "Check for updates" from the menu. Always reports back. */
    fun checkNow(report: (String) -> Unit) {
        scope.launch {
            val result = fetchLatest()
            when {
                result == null -> report("Couldn't check for updates. Check your internet connection and try again.")
                isNewer(result.version, currentVersion) -> _available.value = result
                else -> report("You have the latest version ($currentVersion)")
            }
        }
    }

    fun dismiss() {
        _available.value = null
    }

    /** Don't remind about this version again (a newer one will still be shown). */
    fun skip(version: String) {
        prefs.edit().putString(KEY_SKIPPED, version).apply()
        _available.value = null
    }

    private suspend fun fetchLatest(): AvailableUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MusicSystemManager-Android")
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val version = json.optString("tag_name").removePrefix("v").trim()
                val page = json.optString("html_url")
                // Only ever open this project's own GitHub releases.
                if (version.isEmpty() || !page.startsWith(RELEASES_PAGE_PREFIX)) return@runCatching null
                AvailableUpdate(version, cleanNotes(json.optString("body")), page)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    companion object {
        private const val REPO = "H2209/Music-System-Manager"
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO/releases/latest"
        private const val RELEASES_PAGE_PREFIX = "https://github.com/$REPO/releases/"
        private const val CHECK_INTERVAL_MS = 20 * 60 * 60 * 1000L // about once a day
        private const val KEY_AUTO = "autoCheck"
        private const val KEY_LAST_CHECK = "lastCheck"
        private const val KEY_SKIPPED = "skippedVersion"

        /** True if [candidate] (e.g. "1.0.10") is a higher version than [current] (e.g. "1.0.9"). */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun parts(v: String) =
            v.removePrefix("v").split('.', '-', ' ').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

        /** Release notes as plain text: no markdown symbols, at most ~700 characters. */
        fun cleanNotes(markdown: String): String {
            val text = markdown
                .replace(Regex("""(?m)^#+\s*"""), "")
                .replace("**", "")
                .replace(Regex("""(?m)^\s*[-*]\s+"""), "• ")
                .replace(Regex("""(?<![\w*])[*_]([^*_\n]+)[*_](?![\w*])"""), "$1") // *italic* / _italic_
                .replace("`", "")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()
            return if (text.length > 700) text.take(700).trimEnd() + "…" else text
        }
    }
}
