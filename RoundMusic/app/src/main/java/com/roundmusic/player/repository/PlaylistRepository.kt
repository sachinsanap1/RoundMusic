package com.roundmusic.player.repository

import androidx.room.withTransaction
import com.roundmusic.player.data.db.AppDatabase
import com.roundmusic.player.data.db.PlaylistEntity
import com.roundmusic.player.data.db.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val db: AppDatabase) {

    private val dao = db.playlistDao()

    fun observePlaylists(): Flow<List<PlaylistEntity>> = dao.observePlaylists()
    fun observeAllEntries(): Flow<List<PlaylistSongEntity>> = dao.observeAllEntries()
    fun observeEntries(playlistId: Long): Flow<List<PlaylistSongEntity>> = dao.observeEntries(playlistId)

    /** Creates the playlist and (optionally) puts songs in it. Returns the new playlist id. */
    suspend fun create(name: String, songIds: List<Long> = emptyList()): Long =
        db.withTransaction {
            val id = dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
            if (songIds.isNotEmpty()) addSongsInternal(id, songIds)
            id
        }

    suspend fun rename(playlistId: Long, name: String) = dao.rename(playlistId, name.trim())

    suspend fun delete(playlistId: Long) = dao.delete(playlistId)

    /** Adds songs at the end. Songs already in the playlist are skipped. Returns how many were really added. */
    suspend fun addSongs(playlistId: Long, songIds: List<Long>): Int =
        db.withTransaction { addSongsInternal(playlistId, songIds) }

    suspend fun removeSong(playlistId: Long, songId: Long) = dao.removeSong(playlistId, songId)

    private suspend fun addSongsInternal(playlistId: Long, songIds: List<Long>): Int {
        val now = System.currentTimeMillis()
        var position = dao.maxPosition(playlistId)
        val entries = songIds.distinct().map {
            position += 1
            PlaylistSongEntity(playlistId, it, position, now)
        }
        return dao.insertEntries(entries).count { it != -1L }
    }
}
