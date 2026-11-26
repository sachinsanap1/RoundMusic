package com.roundmusic.player.data.model

import android.net.Uri

/** One audio file found by Android's MediaStore. Only real data from the phone is stored here. */
data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val trackNumber: Int,
    val mimeType: String,
    val displayName: String
)
