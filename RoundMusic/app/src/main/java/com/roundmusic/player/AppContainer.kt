package com.roundmusic.player

import android.content.Context
import com.roundmusic.player.data.artwork.ArtworkLoader
import com.roundmusic.player.data.datastore.SettingsRepository
import com.roundmusic.player.data.db.AppDatabase
import com.roundmusic.player.data.mediastore.MediaStoreScanner
import com.roundmusic.player.data.waveform.WaveformLoader
import com.roundmusic.player.playback.PlaybackController
import com.roundmusic.player.repository.FavoritesRepository
import com.roundmusic.player.repository.LibraryRepository
import com.roundmusic.player.repository.PlaylistRepository
import com.roundmusic.player.repository.QueueRepository
import com.roundmusic.player.repository.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Simple manual "dependency injection": one place that creates the shared objects.
 * Both the screens and the music service use this same container (they live in the same app process).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Lives as long as the app process. Used only for the shared library scan. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase = AppDatabase.create(appContext)

    val settingsRepository = SettingsRepository(appContext)
    val favoritesRepository = FavoritesRepository(database.favoriteDao())
    val playlistRepository = PlaylistRepository(database)
    val statsRepository = StatsRepository(database.statsDao())
    val queueRepository = QueueRepository(database)

    val mediaStoreScanner = MediaStoreScanner(appContext)
    val libraryRepository = LibraryRepository(mediaStoreScanner, appScope)
    val artworkLoader = ArtworkLoader(appContext)
    val waveformLoader = WaveformLoader(appContext)
    val playbackController = PlaybackController(appContext)
}
