package com.roundmusic.player.data.model

import com.roundmusic.player.data.db.QueueItemEntity
import com.roundmusic.player.data.mediastore.MediaUris

/** An album, built from the songs on the phone. Only real data. */
data class AlbumInfo(
    val id: Long,
    val name: String,
    val artist: String,
    val songs: List<Song>
)

/** An artist, built from the songs on the phone. */
data class ArtistInfo(
    val name: String,
    val albumCount: Int,
    val songs: List<Song>
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    /** Only songs that still exist on the phone are counted. */
    val songCount: Int,
    val cover: Song?
)

/** Rebuilds a song from the saved queue (enough details for the player and artwork). */
fun QueueItemEntity.toSong(): Song = Song(
    id = songId,
    uri = MediaUris.songUri(songId),
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    sizeBytes = 0L,
    dateAddedSec = 0L,
    trackNumber = trackNumber,
    mimeType = "",
    displayName = ""
)
