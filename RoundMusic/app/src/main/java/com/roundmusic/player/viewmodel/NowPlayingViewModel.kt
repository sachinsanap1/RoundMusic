package com.roundmusic.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundmusic.player.data.model.Song
import com.roundmusic.player.playback.PlaybackController
import com.roundmusic.player.playback.PlaybackState
import com.roundmusic.player.repository.FavoritesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Connects the mini player and the Now Playing screen to the player and to favorites. */
class NowPlayingViewModel(
    private val playback: PlaybackController,
    private val favorites: FavoritesRepository
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playback.state
    val positionMs: StateFlow<Long> = playback.positionMs
    val queue: StateFlow<List<Song>> = playback.queue

    /** Only the id of the current song, so lists can highlight it without redrawing every half second. */
    val currentSongId: StateFlow<Long?> =
        playback.state
            .map { it.currentSong?.id }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isPlaying: StateFlow<Boolean> =
        playback.state
            .map { it.isPlaying }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val favoriteIds: StateFlow<Set<Long>> =
        favorites.favoriteIds
            .map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun playPause() = playback.togglePlayPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun toggleShuffle() = playback.toggleShuffle()
    fun cycleRepeat() = playback.cycleRepeat()
    fun setVolume(volume: Int) = playback.setVolume(volume)

    fun skipToQueueItem(index: Int, play: Boolean) = playback.skipToQueueItem(index, play)
    fun moveQueueItem(from: Int, to: Int) = playback.moveQueueItem(from, to)
    fun removeQueueItem(index: Int) = playback.removeQueueItem(index)

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { favorites.toggle(songId) }
    }
}
