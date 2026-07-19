package com.xiaowei.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DesktopLyricSettingsDao {

    @Query("SELECT * FROM desktop_lyric_settings WHERE id = 0")
    suspend fun get(): DesktopLyricSettingsEntity?

    @Query("SELECT * FROM desktop_lyric_settings WHERE id = 0")
    fun getSync(): DesktopLyricSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DesktopLyricSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSync(entity: DesktopLyricSettingsEntity)
}
