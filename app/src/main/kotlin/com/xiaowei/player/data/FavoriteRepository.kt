package com.xiaowei.player.data

import com.xiaowei.player.data.db.AppDatabase
import com.xiaowei.player.data.db.FavoriteEntity

class FavoriteRepository(private val db: AppDatabase) {

    private val dao get() = db.favoriteDao()

    fun getFavoriteIdsSync(): Set<Long> = dao.getAllSongIdsSync().toSet()

    suspend fun getFavoriteIds(): Set<Long> = dao.getAllSongIds().toSet()

    fun addSync(songId: Long) {
        dao.insertSync(FavoriteEntity(songId = songId, addedAt = System.currentTimeMillis()))
    }

    suspend fun add(songId: Long) {
        dao.insert(FavoriteEntity(songId = songId, addedAt = System.currentTimeMillis()))
    }

    fun removeSync(songId: Long) {
        dao.deleteSync(songId)
    }

    suspend fun remove(songId: Long) {
        dao.delete(songId)
    }

    fun isFavoriteSync(songId: Long): Boolean = dao.isFavoriteSync(songId)

    suspend fun isFavorite(songId: Long): Boolean = dao.isFavorite(songId)

    fun toggleSync(songId: Long): Boolean {
        return if (isFavoriteSync(songId)) {
            removeSync(songId)
            false
        } else {
            addSync(songId)
            true
        }
    }

    suspend fun toggle(songId: Long): Boolean {
        return if (isFavorite(songId)) {
            remove(songId)
            false
        } else {
            add(songId)
            true
        }
    }
}
