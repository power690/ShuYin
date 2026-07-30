package com.xiaowei.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey
    val id: Int = 0,  

    val songIdsJson: String,
    val currentIndex: Int,
    val positionMs: Long,
    val playModeName: String
)
