package com.xiaowei.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey
    val keyword: String,

    val addedAt: Long = System.currentTimeMillis()
)
