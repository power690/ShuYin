package com.xiaowei.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "desktop_lyric_settings")
data class DesktopLyricSettingsEntity(
    @PrimaryKey
    val id: Int = 0,  
    val fontSize: Float = 20f,
    val textColor: Int = 0xFFFFFFFF.toInt(),  
    val positionX: Int = 0,
    val positionY: Int = 200,
    val enabled: Boolean = false
)
