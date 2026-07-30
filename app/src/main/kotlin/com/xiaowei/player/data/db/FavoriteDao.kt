package com.xiaowei.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoriteDao {

    @Query("SELECT songId FROM favorites")
    suspend fun getAllSongIds(): List<Long>

    @Query("SELECT songId FROM favorites")
    fun getAllSongIdsSync(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun isFavoriteSync(songId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun delete(songId: Long)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    fun deleteSync(songId: Long)

    @Query("DELETE FROM favorites")
    suspend fun clear()

    @Query("DELETE FROM favorites")
    fun clearSync()
}
