# 🎵 Music System Manager

A free Android music player and playlist manager. No ads, no account, no tracking.

**Supports Android 8.0 (API 26) through Android 16 (target API 35).**

## 📥 Download & install (Android phone)

1. On your phone, open the **[latest release](../../releases/latest)** and tap the **`MusicSystemManager-…apk`** file to download it (about 2 MB).
2. Open the downloaded file. If Android asks, allow **"Install unknown apps"** for your browser or Files app. This is normal for apps that don't come from the Play Store.
3. Tap **Install**, then **Open**.
4. In the app, tap **Scan phone for music**, or import files or a folder.

> If Play Protect shows "unrecognised developer", tap **More details → Install anyway**. The app collects nothing about you or your music.

## Features

- **Import music**
  - *Scan phone for music*: finds every song already on the device.
  - *Import files*: pick one or many audio files with the system picker.
  - *Import a folder*: pulls in every audio file in a folder and its sub-folders.
- **Songs and Recordings kept apart**: voice memos, call recordings and voice notes go to their own *Recordings* tab (🎙), so the Songs list is only music. Wrong guess? Use ⋮ → *This is a song / This is a recording*.
- **Cover art**: songs that come with a cover picture (most downloaded MP3s) show it in the list, the mini player and the player page.
- **Search** your whole library by title, artist or album.
- **Every song has buttons:** 👍 **Like**, ▶ **Play** (turns into ⏸ **Pause** on the playing song) and ➕ **Add to playlist**. The ⋮ menu also has *Play next*, *Add to queue* and *Remove from app*.
- **👍 Liked songs**: a built-in playlist, always first on the Playlists tab, holding every song you liked.
- **Playlists with any name**, emojis included (`🔥 Gym Pump`, `🌙 Late night`, `🚗 Road trip`). The name dialog has quick-pick emojis, and the keyboard's emoji key works too.
- **Add songs one by one**: a playlist's *Add* screen lets you search the library and tap ⊕ / ✓ on each song.
- **Mini player** at the bottom. Its ⋮ menu has previous / pause / next, Queue, Sleep timer and Like. Tap it to open the full player.
- **Full player page**: seek bar, previous / play / next, shuffle and repeat (off / all / one), plus 👍 Like, ⏲ Timer and Queue buttons.
- **Sleep timer**: 5, 10, 15, 30, 45, 50 minutes or 1 hour. Music pauses by itself when time is up, even with the screen off or the app closed. A countdown shows in the player.
- **Queue**: song names on the left and a round handle on the right. Drag the handle to move a song up or down; swipe anywhere else to scroll. Shows the next 15 songs, and the rest join as songs finish. Tap a song to play it now.
- **Background playback** with the screen off or the app closed, with a notification and lock-screen controls. Headphone and Bluetooth buttons work. Playback pauses when headphones are unplugged.
- **Update pop-up**: when a new version is published on GitHub, the app says so once and offers **Update / Later / Skip this version**. Library → ⋮ also has *Check for updates* and an on/off switch.
- **Duplicate songs show once, even with different names**: the ⟳ **Refresh** button in the Library listens to your songs (an audio fingerprint of each one, start to end) and finds identical recordings, whatever the file is called. It keeps the best-quality copy as the original, moves likes and playlist places to it, and hides the rest. Nothing is deleted; Library → ⋮ → *Show hidden duplicates* brings them back. Simple cases like "Song (1).mp3" are hidden straight away.
- Material You colours on Android 12+ and dark mode.

## Tech

Kotlin · Jetpack Compose (Material 3) · Media3 ExoPlayer + MediaSession · Room · Navigation Compose

```
app/src/main/java/com/musicsystemmanager/app/
├── MusicApp.kt / MainActivity.kt   app entry + dependency container
├── data/                           Room entities, DAOs, MusicRepository (scan/import/playlists)
├── playback/                       PlaybackService (background player), PlayerConnection (UI ↔ player)
└── ui/                             library, playlists, player screens + shared components
```

## Build & run

Requirements: **Android Studio** (Ladybug or newer). It bundles the JDK 17 that the build needs.

1. Open this folder in Android Studio and let Gradle sync.
2. Plug in a phone with USB debugging on, or start an emulator (Android 8+).
3. Press ▶ **Run**.

From the command line, with JDK 17+ on `JAVA_HOME`:

```bash
gradle wrapper --gradle-version 8.11.1   # one time, creates ./gradlew
./gradlew assembleDebug                  # APK: app/build/outputs/apk/debug/app-debug.apk
```

### Releasing an APK people can download

The release build is signed with a key kept **outside** this repository, in
`~/MusicSystemManager-signing-key/` (`release.jks` + `keystore.properties`). The build finds it automatically.

```bash
./gradlew assembleRelease   # APK: app/build/outputs/apk/release/app-release.apk
```

⚠️ Back up that key folder. Every future update must be signed with the **same** key, or phones will refuse to install it over the old version.
Without the key folder, `assembleRelease` produces an unsigned APK.

## Permissions (and why)

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` (13+) / `READ_EXTERNAL_STORAGE` (8–12) | Only for *Scan phone for music*. Importing files or folders works without it. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` | Keep music playing with the screen off. |
| `POST_NOTIFICATIONS` (13+) | Show the play/pause/next notification. |
| `INTERNET` | Only to ask GitHub whether a newer version exists (about once a day). It sends nothing about you or your music. Turn it off in Library → ⋮ → *Check automatically*. |

The app collects no data. Your music, playlists and likes never leave your phone.

## Roadmap ideas

- Album art from file tags
- Drag to reorder songs in a playlist
- Remember the queue and position after the app closes
- Equalizer, sleep timer
- Export and import playlists (M3U)
