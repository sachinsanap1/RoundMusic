package com.roundmusic.player.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.roundmusic.player.data.model.Song
import com.roundmusic.player.service.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The screen's remote control for the music player.
 * The real player lives inside [MusicService]; this class only connects to it and sends commands.
 * MainActivity calls [connect] in onStart and [disconnect] in onStop, so nothing is left connected.
 */
class PlaybackController(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    /** Current play position in milliseconds (updates twice a second while playing). */
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    /** The songs in the play queue, in queue order. Only updates when the queue itself changes. */
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** Short messages for the user, for example "this song could not be played". */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) publishQueue()
        }

        override fun onPlayerError(error: PlaybackException) {
            _messages.tryEmit("Couldn't play that song. It may be damaged or missing. Skipping.")
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
        val newFuture = MediaController.Builder(appContext, token).buildAsync()
        future = newFuture
        newFuture.addListener(
            {
                if (future !== newFuture) return@addListener // disconnect() was called meanwhile
                try {
                    val connected = newFuture.get()
                    controller = connected
                    connected.addListener(listener)
                    publish()
                    publishQueue()
                    startTicker()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not connect to the music service", e)
                    future = null
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun disconnect() {
        ticker?.cancel()
        ticker = null
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    // ---------------------------------------------------------------- playing

    /**
     * Plays [songs] as the queue, starting at [startIndex].
     * [forceShuffle] = true turns Shuffle on (used by the "Shuffle" buttons).
     */
    fun playSongs(songs: List<Song>, startIndex: Int, forceShuffle: Boolean = false) {
        val c = controller ?: return
        if (startIndex !in songs.indices) return

        val tapped = songs[startIndex]
        if (!forceShuffle && c.mediaItemCount > 0 && c.currentMediaItem?.mediaId == tapped.id.toString()) {
            togglePlayPause() // tapping the song that is already loaded: just play/pause it
            return
        }
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        if (forceShuffle) c.shuffleModeEnabled = true
        c.prepare()
        c.play()
    }

    /** Puts [song] at the end of the queue. If nothing is queued yet, it becomes the queue (paused). */
    fun addToQueue(song: Song): Boolean {
        val c = controller ?: return false
        val item = song.toMediaItem()
        if (c.mediaItemCount == 0) {
            c.setMediaItem(item)
            c.prepare()
        } else {
            c.addMediaItem(item)
        }
        return true
    }

    /** Puts [song] right after the song that is playing. */
    fun playNext(song: Song): Boolean {
        val c = controller ?: return false
        val item = song.toMediaItem()
        if (c.mediaItemCount == 0) {
            c.setMediaItem(item)
            c.prepare()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, item)
        }
        return true
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            when (c.playbackState) {
                Player.STATE_IDLE -> c.prepare()
                Player.STATE_ENDED -> c.seekToDefaultPosition(0)
            }
            c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious() // restarts the song if it is past 3 seconds, otherwise goes back
    }

    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs.coerceAtLeast(0L))
        _positionMs.value = positionMs.coerceAtLeast(0L)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    /** Repeat: off -> all -> one -> off. */
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Sets the phone's media volume (the same volume the hardware buttons change). */
    fun setVolume(volume: Int) {
        val c = controller ?: return
        c.setDeviceVolume(volume, 0)
    }

    // ------------------------------------------------------------------ queue

    /** Jumps to the song at [index] in the queue. [play] = start playing even if it was paused. */
    fun skipToQueueItem(index: Int, play: Boolean) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekToDefaultPosition(index)
        if (play) c.play()
    }

    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        val last = c.mediaItemCount - 1
        if (from !in 0..last || to !in 0..last || from == to) return
        c.moveMediaItem(from, to)
    }

    fun removeQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.removeMediaItem(index)
    }

    // --------------------------------------------------------------- internals

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) _positionMs.value = c.currentPosition.coerceAtLeast(0L)
                delay(500)
            }
        }
    }

    private fun publish() {
        val c = controller ?: return
        val song = c.currentMediaItem?.toSongOrNull()
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: song?.durationMs ?: 0L
        val volumeAvailable = c.isCommandAvailable(Player.COMMAND_GET_DEVICE_VOLUME) &&
            c.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
        val info = c.deviceInfo

        _state.value = PlaybackState(
            isConnected = true,
            currentSong = song,
            isPlaying = c.isPlaying,
            durationMs = duration,
            hasNext = c.hasNextMediaItem(),
            queueSize = c.mediaItemCount,
            queueIndex = c.currentMediaItemIndex.coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            volumeAvailable = volumeAvailable,
            volume = if (volumeAvailable) c.deviceVolume else 0,
            minVolume = info.minVolume,
            maxVolume = info.maxVolume
        )
        _positionMs.value = c.currentPosition.coerceAtLeast(0L)
    }

    private fun publishQueue() {
        val c = controller ?: return
        _queue.value = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).toSongOrNull() }
    }

    private companion object {
        const val TAG = "PlaybackController"
    }
}
