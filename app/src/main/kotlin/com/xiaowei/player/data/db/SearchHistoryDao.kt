package com.xiaowei.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchHistoryDao {

    @Query("SELECT keyword FROM search_history ORDER BY addedAt DESC LIMIT 10")
    suspend fun getRecent(): List<String>

    @Query("SELECT keyword FROM search_history ORDER BY addedAt DESC LIMIT 10")
    fun getRecentSync(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SearchHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Query("DELETE FROM search_history")
    fun clearSync()
}
