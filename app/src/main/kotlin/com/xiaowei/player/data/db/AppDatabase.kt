package com.xiaowei.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        PlaybackStateEntity::class,
        UserProfileEntity::class,
        SearchHistoryEntity::class,
        DesktopLyricSettingsEntity::class
    ],
    version = 3,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profile ADD COLUMN profileCompletedAt INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profile ADD COLUMN loginStreak INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN lastLoginDay INTEGER")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN songCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shuyin.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)

                    .allowMainThreadQueries()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
