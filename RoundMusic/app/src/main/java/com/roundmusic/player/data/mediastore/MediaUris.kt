package com.roundmusic.player.data.mediastore

import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** The one place that knows how to build the address of an audio file in MediaStore. */
object MediaUris {

    val audioCollection: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    fun songUri(id: Long): Uri = ContentUris.withAppendedId(audioCollection, id)
}
