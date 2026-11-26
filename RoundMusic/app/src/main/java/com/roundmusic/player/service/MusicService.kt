package com.roundmusic.player.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.roundmusic.player.MainActivity
import com.roundmusic.player.R
import com.roundmusic.player.RoundMusicApp
import com.roundmusic.player.data.datastore.ResumeState
import com.roundmusic.player.data.db.QueueItemEntity
import com.roundmusic.player.data.mediastore.MediaUris
import com.roundmusic.player.data.model.toSong
import com.roundmusic.player.playback.toMediaItem
import com.roundmusic.player.playback.toSongOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * THE music player. It owns the ExoPlayer and the MediaSession.
 *
 * Because the player lives in a service (not in a screen):
 *  - music keeps playing with the screen off or the app in the background,
 *  - Android builds the notification and lock-screen player FROM this session,
 *  - Bluetooth / headset buttons and (Phase 7) Android Auto talk to this session.
 *
 * Phase 4 adds here: the Favorite button, counting plays (for Most played / Recently played),
 * saving the queue, and resuming the last queue.
 */
@UnstableApi
class MusicService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private var bitmapLoader: SongBitmapLoader? = null
    private lateinit var player: ExoPlayer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as RoundMusicApp).container

    private var consecutiveErrors = 0
    private var favoriteIds: Set<Long> = emptySet()

    // Saving / restoring the queue
    private var restoreDone = false
    private var saveQueueJob: Job? = null

    // Counting plays
    private var countedThisItem = false
    private var trackerJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true      // pauses for calls/other apps, resumes after, ducks for short sounds
            )
            .setHandleAudioBecomingNoisy(true)      // pauses when headphones are unplugged / Bluetooth disconnects
            .setWakeMode(C.WAKE_MODE_LOCAL)         // keeps playing with the screen off
            .build()

        player.addListener(PlayerEvents())

        val loader = SongBitmapLoader(this)
        bitmapLoader = loader

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setBitmapLoader(loader)
            .setSessionActivity(openAppIntent)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) }
        )

        refreshButtons()

        // Keep the notification's heart in sync with the favorites in the database.
        serviceScope.launch {
            container.favoritesRepository.favoriteIds.collect { ids ->
                favoriteIds = ids.toSet()
                refreshButtons()
            }
        }

        restoreSavedQueue()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    /**
     * The user swiped the app away from "recent apps".
     * If music is playing we keep playing (like most music players); otherwise we stop the service.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Save the queue and position one last time (in a short job that is not tied to this service).
        if (restoreDone) {
            val items = collectQueue()
            val state = currentResumeState()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    container.queueRepository.replace(items)
                    container.settingsRepository.saveResumeState(state)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not save the queue on shutdown", e)
                }
            }
        }
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        bitmapLoader?.shutdown()
        bitmapLoader = null
        super.onDestroy()
    }

    // ------------------------------------------------------------- notification buttons

    /** Shuffle, Favorite (heart) and Repeat buttons for the notification / lock screen. */
    private fun refreshButtons() {
        val shuffleOn = player.shuffleModeEnabled
        val shuffle = CommandButton.Builder(
            if (shuffleOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setDisplayName(getString(R.string.action_shuffle))
            .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE, !shuffleOn)
            .build()

        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val isFavorite = currentId != null && currentId in favoriteIds
        val favorite = CommandButton.Builder(
            if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(getString(R.string.action_favorite))
            .setSessionCommand(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
            .build()

        val repeatIcon: Int
        val nextRepeatMode: Int
        when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> {
                repeatIcon = CommandButton.ICON_REPEAT_OFF
                nextRepeatMode = Player.REPEAT_MODE_ALL
            }
            Player.REPEAT_MODE_ALL -> {
                repeatIcon = CommandButton.ICON_REPEAT_ALL
                nextRepeatMode = Player.REPEAT_MODE_ONE
            }
            else -> {
                repeatIcon = CommandButton.ICON_REPEAT_ONE
                nextRepeatMode = Player.REPEAT_MODE_OFF
            }
        }
        val repeat = CommandButton.Builder(repeatIcon)
            .setDisplayName(getString(R.string.action_repeat))
            .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, nextRepeatMode)
            .build()

        // Phones show only the first two extra buttons, so the order matters: shuffle, favorite, repeat.
        mediaSession?.setMediaButtonPreferences(ImmutableList.of(shuffle, favorite, repeat))
    }

    // ------------------------------------------------------- saving and restoring the queue

    private data class ResumeData(
        val items: List<MediaItem>,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeatMode: Int
    )

    private suspend fun loadResume(): ResumeData? = withContext(Dispatchers.IO) {
        val rows = container.queueRepository.load()
        if (rows.isEmpty()) return@withContext null
        val state = container.settingsRepository.loadResumeState()
        ResumeData(
            items = rows.map { it.toSong().toMediaItem() },
            index = state.index.coerceIn(0, rows.lastIndex),
            positionMs = state.positionMs.coerceAtLeast(0L),
            shuffle = state.shuffle,
            repeatMode = state.repeatMode
        )
    }

    /** When the service starts with an empty player, load the last queue (paused) so you can continue. */
    private fun restoreSavedQueue() {
        serviceScope.launch {
            try {
                val saved = loadResume()
                if (saved != null && player.mediaItemCount == 0) {
                    player.shuffleModeEnabled = saved.shuffle
                    player.repeatMode = saved.repeatMode
                    player.setMediaItems(saved.items, saved.index, saved.positionMs)
                    player.prepare() // stays paused: playWhenReady is false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore the saved queue", e)
            } finally {
                restoreDone = true
            }
        }
    }

    private fun collectQueue(): List<QueueItemEntity> {
        val result = ArrayList<QueueItemEntity>(player.mediaItemCount)
        for (i in 0 until player.mediaItemCount) {
            val song = player.getMediaItemAt(i).toSongOrNull() ?: continue
            result.add(
                QueueItemEntity(
                    position = result.size,
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    albumId = song.albumId,
                    durationMs = song.durationMs,
                    trackNumber = song.trackNumber
                )
            )
        }
        return result
    }

    private fun currentResumeState() = ResumeState(
        index = player.currentMediaItemIndex.coerceAtLeast(0),
        positionMs = player.currentPosition.coerceAtLeast(0L),
        shuffle = player.shuffleModeEnabled,
        repeatMode = player.repeatMode
    )

    /** Waits one second after the last queue change, then saves the whole queue. */
    private fun scheduleQueueSave() {
        if (!restoreDone) return
        saveQueueJob?.cancel()
        saveQueueJob = serviceScope.launch {
            delay(1_000)
            val items = collectQueue()
            val state = currentResumeState()
            withContext(Dispatchers.IO) {
                container.queueRepository.replace(items)
                container.settingsRepository.saveResumeState(state)
            }
        }
    }

    private fun saveResumeState() {
        if (!restoreDone) return
        val state = currentResumeState()
        serviceScope.launch(Dispatchers.IO) {
            container.settingsRepository.saveResumeState(state)
        }
    }

    // ----------------------------------------------------------------- counting plays

    private fun startTracker() {
        trackerJob?.cancel()
        trackerJob = serviceScope.launch {
            var ticks = 0
            while (isActive) {
                delay(1_000)
                countPlayIfNeeded()
                ticks++
                if (ticks % 10 == 0) saveResumeState() // keeps the saved position fresh while playing
            }
        }
    }

    /** A song counts as "played" after 30 seconds, or half of it for short songs. */
    private fun countPlayIfNeeded() {
        if (countedThisItem) return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val threshold = min(30_000L, duration / 2)
        if (player.currentPosition < threshold) return
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        countedThisItem = true
        serviceScope.launch(Dispatchers.IO) {
            try {
                container.statsRepository.recordPlay(id)
            } catch (e: Exception) {
                Log.w(TAG, "Could not record the play", e)
            }
        }
    }

    // ----------------------------------------------------------------------- listeners

    private inner class PlayerEvents : Player.Listener {

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            refreshButtons()
            saveResumeState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            refreshButtons()
            saveResumeState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            countedThisItem = false
            refreshButtons() // the heart shows the favorite state of the NEW song
            saveResumeState()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) scheduleQueueSave()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                consecutiveErrors = 0
                startTracker()
            } else {
                trackerJob?.cancel()
                trackerJob = null
                saveResumeState()
            }
        }

        /** A file is corrupted, deleted or unreadable: skip to the next song instead of getting stuck. */
        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback error: ${error.errorCodeName}", error)
            consecutiveErrors++
            if (player.hasNextMediaItem() && consecutiveErrors < player.mediaItemCount) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
            // else: everything left is broken (or this was the last song), so we stay stopped.
        }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        /**
         * Only apps that should control the player may connect:
         * this app, Android's own trusted parts (notification, lock screen, Bluetooth, Android Auto).
         * Everyone else gets Media3's default (read-only) access.
         */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val allowed = controller.packageName == this@MusicService.packageName ||
                controller.isTrusted ||
                session.isMediaNotificationController(controller)
            if (!allowed) return super.onConnect(session, controller)

            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        /** The heart button in the notification / lock screen. */
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_TOGGLE_FAVORITE) {
                val id = player.currentMediaItem?.mediaId?.toLongOrNull()
                if (id != null) {
                    serviceScope.launch(Dispatchers.IO) { container.favoritesRepository.toggle(id) }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        /**
         * Apps can send songs that only have an id. This fills in the real file address.
         * (Android Auto and Bluetooth browsing in Phase 7 use this too.)
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    val uri = item.requestMetadata.mediaUri
                        ?: item.mediaId.toLongOrNull()?.let { MediaUris.songUri(it) }
                    if (uri != null) item.buildUpon().setUri(uri).build() else item
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        /**
         * A Bluetooth / headset play button was pressed while nothing is loaded
         * (for example the app was closed): hand the player the last saved queue.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                try {
                    val saved = loadResume()
                    if (saved == null) {
                        future.setException(UnsupportedOperationException("Nothing to resume"))
                    } else {
                        future.set(MediaSession.MediaItemsWithStartPosition(saved.items, saved.index, saved.positionMs))
                    }
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }
    }

    private companion object {
        const val TAG = "MusicService"
        const val CMD_TOGGLE_FAVORITE = "com.roundmusic.player.TOGGLE_FAVORITE"
    }
}
