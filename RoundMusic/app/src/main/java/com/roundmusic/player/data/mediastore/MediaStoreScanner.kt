package com.roundmusic.player.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.roundmusic.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Reads audio files from Android's MediaStore.
 * MediaStore already includes the SD card (if Android has indexed it), so we never touch file paths.
 */
class MediaStoreScanner(context: Context) {

    private val resolver: ContentResolver = context.applicationContext.contentResolver

    private val collection: Uri = MediaUris.audioCollection

    /**
     * Emits a value every time Android's audio database changes (new file, deleted file, tags edited).
     * The observer is removed automatically when nobody collects the flow, so it cannot leak.
     */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(collection, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /** Reads every music file once. Rows that cannot be read are skipped instead of crashing the app. */
    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = ArrayList<Song>()
        val cursor = resolver.query(collection, PROJECTION, SELECTION, null, null)
            ?: return@withContext songs

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (c.moveToNext()) {
                ensureActive() // lets a newer scan cancel this one quickly
                try {
                    val id = c.getLong(idCol)
                    val displayName = c.getString(nameCol).orEmpty()
                    val rawTitle = c.getString(titleCol)
                    val title = rawTitle?.takeIf { it.isNotBlank() }
                        ?: displayName.substringBeforeLast('.', displayName).ifBlank { "Unknown title" }

                    songs.add(
                        Song(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            title = title,
                            artist = cleanUnknown(c.getString(artistCol), "Unknown artist"),
                            album = cleanUnknown(c.getString(albumCol), "Unknown album"),
                            albumId = c.getLong(albumIdCol),
                            durationMs = c.getLong(durationCol),
                            sizeBytes = c.getLong(sizeCol),
                            dateAddedSec = c.getLong(addedCol),
                            // MediaStore stores disc*1000 + track, so keep only the track part.
                            trackNumber = c.getInt(trackCol) % 1000,
                            mimeType = c.getString(mimeCol).orEmpty(),
                            displayName = displayName
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping a row that could not be read", e)
                }
            }
        }
        songs
    }

    private fun cleanUnknown(value: String?, fallback: String): String =
        if (value.isNullOrBlank() || value == "<unknown>") fallback else value

    private companion object {
        const val TAG = "MediaStoreScanner"

        val PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME
        )

        // Only real music (not ringtones/alarms) and only files Android could read a length from.
        const val SELECTION =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
    }
}
