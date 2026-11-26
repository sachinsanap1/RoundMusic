package com.roundmusic.player.playback

import com.roundmusic.player.data.model.Song

/**
 * A snapshot of what the player is doing right now. The screens only read this.
 * The play position is NOT in here: it changes twice a second, so it has its own flow
 * ([PlaybackController.positionMs]). That way the screen does not redraw everything constantly.
 */
data class PlaybackState(
    val isConnected: Boolean = false,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val queueSize: Int = 0,
    /** Position of the current song in the queue (0 = first). */
    val queueIndex: Int = 0,
    val shuffle: Boolean = false,
    /** One of Player.REPEAT_MODE_OFF / ONE / ALL. */
    val repeatMode: Int = 0,
    val volumeAvailable: Boolean = false,
    val volume: Int = 0,
    val minVolume: Int = 0,
    val maxVolume: Int = 0
)
