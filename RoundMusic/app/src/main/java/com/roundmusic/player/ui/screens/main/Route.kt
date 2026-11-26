package com.roundmusic.player.ui.screens.main

/** The smart playlists. They fill themselves from your songs and your play history. */
enum class SmartKind(val title: String) {
    FAVORITES("Liked Music"),
    RECENTLY_ADDED("Recently added"),
    RECENTLY_PLAYED("Recently played"),
    MOST_PLAYED("Most played")
}

/**
 * Pages that open on top of a tab (playlist, album, artist...).
 * They are saved as simple text like "playlist/12" so they survive screen rotation.
 */
sealed interface Route {
    data class Playlist(val id: Long) : Route
    data class Album(val id: Long) : Route
    data class Artist(val name: String) : Route
    data class Smart(val kind: SmartKind) : Route
    data class Picker(val playlistId: Long) : Route

    fun encode(): String = when (this) {
        is Playlist -> "playlist/$id"
        is Album -> "album/$id"
        is Artist -> "artist/$name"
        is Smart -> "smart/${kind.name}"
        is Picker -> "picker/$playlistId"
    }

    companion object {
        fun parse(text: String): Route? {
            val type = text.substringBefore('/')
            val value = text.substringAfter('/', "")
            return when (type) {
                "playlist" -> value.toLongOrNull()?.let { Playlist(it) }
                "album" -> value.toLongOrNull()?.let { Album(it) }
                "artist" -> Artist(value)
                "smart" -> SmartKind.entries.firstOrNull { it.name == value }?.let { Smart(it) }
                "picker" -> value.toLongOrNull()?.let { Picker(it) }
                else -> null
            }
        }
    }
}
