package com.xiaowei.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaybackStateDao {

    @Query("SELECT * FROM playback_state WHERE id = 0")
    suspend fun get(): PlaybackStateEntity?

    @Query("SELECT * FROM playback_state WHERE id = 0")
    fun getSync(): PlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSync(entity: PlaybackStateEntity)

    @Query("DELETE FROM playback_state")
    suspend fun clear()

    @Query("DELETE FROM playback_state")
    fun clearSync()
}
