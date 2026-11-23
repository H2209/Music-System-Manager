package com.musicsystemmanager.app

import android.app.Application
import android.content.Context
import com.musicsystemmanager.app.data.ArtworkLoader
import com.musicsystemmanager.app.data.MusicDatabase
import com.musicsystemmanager.app.data.MusicRepository
import com.musicsystemmanager.app.playback.PlayerConnection
import com.musicsystemmanager.app.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Simple manual dependency container shared by the whole app. */
class AppContainer(context: Context) {
    /** Scope for work that must finish even if the screen that started it goes away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database = MusicDatabase.build(context)
    val repository = MusicRepository(context, database.songDao(), database.playlistDao(), database.fingerprintDao())
    val player = PlayerConnection(context)
    val artwork = ArtworkLoader(context)
    val updates = UpdateChecker(context, appScope)
}
