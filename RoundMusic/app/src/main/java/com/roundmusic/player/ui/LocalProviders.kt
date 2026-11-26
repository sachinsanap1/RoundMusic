package com.roundmusic.player.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.roundmusic.player.data.model.Song

/**
 * Things almost every screen needs, handed down without passing them through every function.
 */

/** Wraps "play" actions. The root uses it to ask for the notification permission the first time. */
val LocalPlayGate = staticCompositionLocalOf<(() -> Unit) -> Unit> { { play -> play() } }

/** The id of the song that is loaded in the player (its row is drawn in purple). */
val LocalCurrentSongId = compositionLocalOf<Long?> { null }

/** True while music is actually playing (the current song's round button shows "pause"). */
val LocalIsPlaying = compositionLocalOf { false }

/** What the "three dots" menu of a song can do. */
@Immutable
class SongActions(
    val favoriteIds: Set<Long>,
    val toggleFavorite: (Song) -> Unit,
    val playNext: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
    val addToPlaylist: (List<Song>) -> Unit
)

val LocalSongActions = staticCompositionLocalOf<SongActions> {
    error("SongActions was not provided")
}
