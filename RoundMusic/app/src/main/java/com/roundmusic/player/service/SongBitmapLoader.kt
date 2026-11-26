package com.roundmusic.player.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Gives Media3 the album-art picture for the notification and lock screen.
 * The "artwork address" of each song is the song's own MediaStore address, so we ask Android
 * for that song's thumbnail (Android 10+) or read the picture stored inside the file (older).
 */
@UnstableApi
class SongBitmapLoader(context: Context) : BitmapLoader {

    private val appContext = context.applicationContext
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit(Callable<Bitmap> {
            BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: throw IllegalStateException("Could not decode artwork bytes")
        })

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        executor.submit(Callable<Bitmap> { load(uri) })

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun load(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return appContext.contentResolver.loadThumbnail(uri, Size(SIZE_PX, SIZE_PX), null)
            } catch (e: Exception) {
                // No thumbnail: fall through and try the picture stored inside the file.
            }
        }
        return embeddedPicture(uri) ?: throw IllegalStateException("No artwork for $uri")
    }

    private fun embeddedPicture(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val SIZE_PX = 512
    }
}
