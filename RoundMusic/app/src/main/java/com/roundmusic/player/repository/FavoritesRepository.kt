package com.roundmusic.player.repository

import com.roundmusic.player.data.db.FavoriteDao
import com.roundmusic.player.data.db.FavoriteEntity
import kotlinx.coroutines.flow.Flow

class FavoritesRepository(private val dao: FavoriteDao) {

    /** Favorite song ids, newest first. */
    val favoriteIds: Flow<List<Long>> = dao.observeIds()

    /** Adds the song if it is not a favorite, removes it if it is. Returns the new state. */
    suspend fun toggle(songId: Long): Boolean {
        return if (dao.isFavorite(songId)) {
            dao.delete(songId)
            false
        } else {
            dao.insert(FavoriteEntity(songId, System.currentTimeMillis()))
            true
        }
    }
}
