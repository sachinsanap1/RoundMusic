package com.roundmusic.player.repository

import com.roundmusic.player.data.db.PlayStatsEntity
import com.roundmusic.player.data.db.StatsDao
import kotlinx.coroutines.flow.Flow

class StatsRepository(private val dao: StatsDao) {

    fun observeAll(): Flow<List<PlayStatsEntity>> = dao.observeAll()

    /** Counts one play of this song (called by the player after about 30 seconds of listening). */
    suspend fun recordPlay(songId: Long) {
        val now = System.currentTimeMillis()
        if (dao.increment(songId, now) == 0) {
            dao.insert(PlayStatsEntity(songId, playCount = 1, lastPlayedAt = now))
        }
    }
}
