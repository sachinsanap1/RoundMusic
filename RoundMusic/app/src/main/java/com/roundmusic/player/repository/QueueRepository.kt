package com.roundmusic.player.repository

import androidx.room.withTransaction
import com.roundmusic.player.data.db.AppDatabase
import com.roundmusic.player.data.db.QueueItemEntity

/** Saves and loads the play queue so it survives closing the app. */
class QueueRepository(private val db: AppDatabase) {

    private val dao = db.queueDao()

    suspend fun replace(items: List<QueueItemEntity>) {
        db.withTransaction {
            dao.clear()
            if (items.isNotEmpty()) dao.insertAll(items)
        }
    }

    suspend fun load(): List<QueueItemEntity> = dao.getAll()
}
