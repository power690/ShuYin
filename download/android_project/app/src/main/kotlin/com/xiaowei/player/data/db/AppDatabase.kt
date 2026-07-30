package com.xiaowei.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        PlaybackStateEntity::class,
        UserProfileEntity::class,
        SearchHistoryEntity::class,
        DesktopLyricSettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun desktopLyricSettingsDao(): DesktopLyricSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shuyin.db"
                )
                    .fallbackToDestructiveMigration(true)

                    .allowMainThreadQueries()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
