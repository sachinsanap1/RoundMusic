package com.roundmusic.player.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.roundmusic.player.data.mediastore.MediaUris
import com.roundmusic.player.data.model.Song

private const val EXTRA_ALBUM_ID = "album_id"

/** Turns one of our songs into the object the Media3 player understands. */
fun Song.toMediaItem(): MediaItem {
    val extras = Bundle().apply { putLong(EXTRA_ALBUM_ID, albumId) }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setTrackNumber(trackNumber.takeIf { it > 0 })
        .setDurationMs(durationMs)
        // The artwork address is the song itself. Our own BitmapLoader (service package)
        // knows how to turn it into a picture for the notification and lock screen.
        .setArtworkUri(uri)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}

/** Rebuilds enough of a [Song] (title, artist, artwork key...) to show it in the mini player. */
fun MediaItem.toSongOrNull(): Song? {
    val id = mediaId.toLongOrNull() ?: return null
    val md = mediaMetadata
    return Song(
        id = id,
        uri = MediaUris.songUri(id),
        title = md.title?.toString().orEmpty(),
        artist = md.artist?.toString().orEmpty(),
        album = md.albumTitle?.toString().orEmpty(),
        albumId = md.extras?.getLong(EXTRA_ALBUM_ID) ?: 0L,
        durationMs = md.durationMs ?: 0L,
        sizeBytes = 0L,
        dateAddedSec = 0L,
        trackNumber = md.trackNumber ?: 0,
        mimeType = "",
        displayName = ""
    )
}
