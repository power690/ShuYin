package com.xiaowei.player.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 0,
    val name: String,
    val avatarUri: String? = null,
    val profileCompletedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val loginStreak: Int = 0,
    val lastLoginDay: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val songCount: Int = 0
)
