package com.roundmusic.player.repository

import android.util.Log
import com.roundmusic.player.data.mediastore.MediaStoreScanner
import com.roundmusic.player.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

sealed interface LibraryState {
    data class Ready(val songs: List<Song>) : LibraryState
    data class Error(val message: String) : LibraryState
}

class LibraryRepository(
    private val scanner: MediaStoreScanner,
    scope: CoroutineScope
) {

    private val manualRefresh = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Re-reads the library now (used by the Rescan button). */
    fun refresh() {
        manualRefresh.tryEmit(Unit)
    }

    /**
     * ONE shared scan for all screens (Home, Search, Library...). It reads the library when the first
     * screen needs it, again whenever Android's audio database changes (after 1 second of quiet),
     * and stops 10 seconds after the last screen goes away. New screens get the last result at once.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val shared: SharedFlow<LibraryState> =
        merge(scanner.changes().debounce(1_000), manualRefresh)
            .onStart { emit(Unit) }
            .mapLatest {
                try {
                    LibraryState.Ready(scanner.loadSongs()) as LibraryState
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("LibraryRepository", "Library scan failed", e)
                    LibraryState.Error("Could not read your music library. Please try again.")
                }
            }
            .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10_000), replay = 1)

    fun observeLibrary(): Flow<LibraryState> = shared
}
