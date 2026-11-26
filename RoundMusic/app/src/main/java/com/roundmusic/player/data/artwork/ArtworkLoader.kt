package com.roundmusic.player.data.artwork

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.roundmusic.player.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Loads album-art pictures: small ones for lists, large ones for the Now Playing screen.
 * - Android 10+ : asks MediaStore for a thumbnail of the song (embedded art or folder art).
 * - Android 8-9 : reads the old album-art address.
 * Pictures are cached in memory (limited size). Songs without art simply return null.
 */
class ArtworkLoader(context: Context) {

    private val resolver: ContentResolver = context.applicationContext.contentResolver

    private val cache = object : LruCache<Long, Bitmap>(maxCacheKb()) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
    }

    // Albums or songs we already know have no picture, so we do not ask again.
    private val missing = ConcurrentHashMap.newKeySet<Long>()

    // At most 4 pictures are decoded at the same time (protects battery and memory while scrolling).
    private val limiter = Semaphore(4)

    fun cached(song: Song, large: Boolean = false): Bitmap? = cache.get(keyOf(song, large))

    suspend fun load(song: Song, large: Boolean = false): Bitmap? {
        val key = keyOf(song, large)
        cache.get(key)?.let { return it }
        if (key in missing) return null

        return limiter.withPermit {
            withContext(Dispatchers.IO) {
                val bitmap = try {
                    decode(song, if (large) LARGE_PX else SMALL_PX)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null // no artwork, corrupted artwork, or file gone: show the placeholder
                }
                if (bitmap != null) cache.put(key, bitmap) else missing.add(key)
                bitmap
            }
        }
    }

    fun clear() {
        cache.evictAll()
    }

    // Songs of one album share one picture. Songs without an album id use their own id (negative number).
    // The last bit tells small (0) and large (1) pictures apart.
    private fun keyOf(song: Song, large: Boolean): Long {
        val base = if (song.albumId > 0) song.albumId else -song.id
        return base * 2 + (if (large) 1 else 0)
    }

    private fun decode(song: Song, sizePx: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.loadThumbnail(song.uri, Size(sizePx, sizePx), null)
        } else {
            decodeSampled(ContentUris.withAppendedId(LEGACY_ALBUM_ART, song.albumId), sizePx)
        }

    private fun decodeSampled(uri: Uri, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null

        var sample = 1
        while (longest / (sample * 2) >= sizePx) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private companion object {
        const val SMALL_PX = 320
        const val LARGE_PX = 1024
        val LEGACY_ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}

private fun maxCacheKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()
