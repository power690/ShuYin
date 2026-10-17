package com.xiaowei.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 0")
    suspend fun get(): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE id = 0")
    fun getSync(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSync(entity: UserProfileEntity)

    @Query("UPDATE user_profile SET name = :name WHERE id = 0")
    suspend fun updateName(name: String)

    @Query("UPDATE user_profile SET name = :name WHERE id = 0")
    fun updateNameSync(name: String)

    @Query("UPDATE user_profile SET avatarUri = :uri WHERE id = 0")
    suspend fun updateAvatarUri(uri: String?)

    @Query("UPDATE user_profile SET avatarUri = :uri WHERE id = 0")
    fun updateAvatarUriSync(uri: String?)

    @Query("UPDATE user_profile SET profileCompletedAt = :ts WHERE id = 0")
    suspend fun updateProfileCompletedAt(ts: Long?)

    @Query("UPDATE user_profile SET profileCompletedAt = :ts WHERE id = 0")
    fun updateProfileCompletedAtSync(ts: Long?)

    @Query("UPDATE user_profile SET loginStreak = :streak WHERE id = 0")
    suspend fun updateLoginStreak(streak: Int)

    @Query("UPDATE user_profile SET loginStreak = :streak WHERE id = 0")
    fun updateLoginStreakSync(streak: Int)

    @Query("UPDATE user_profile SET lastLoginDay = :day WHERE id = 0")
    suspend fun updateLastLoginDay(day: Long?)

    @Query("UPDATE user_profile SET lastLoginDay = :day WHERE id = 0")
    fun updateLastLoginDaySync(day: Long?)

    @Query("UPDATE user_profile SET songCount = :count WHERE id = 0")
    suspend fun updateSongCount(count: Int)

    @Query("UPDATE user_profile SET songCount = :count WHERE id = 0")
    fun updateSongCountSync(count: Int)
}
