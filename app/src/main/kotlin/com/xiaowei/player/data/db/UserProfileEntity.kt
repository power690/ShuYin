package com.xiaowei.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 0,  
    val name: String,
    val avatarUri: String? = null
)
