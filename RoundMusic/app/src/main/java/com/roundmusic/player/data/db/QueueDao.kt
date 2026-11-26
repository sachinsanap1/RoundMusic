package com.roundmusic.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface QueueDao {

    @Query("SELECT * FROM queue_items ORDER BY position")
    suspend fun getAll(): List<QueueItemEntity>

    @Query("DELETE FROM queue_items")
    suspend fun clear()

    @Insert
    suspend fun insertAll(items: List<QueueItemEntity>)
}
