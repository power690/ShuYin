package com.xiaowei.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import java.io.File
import com.xiaowei.player.data.FavoriteRepository
import com.xiaowei.player.data.MusicRepository
import com.xiaowei.player.data.PlaybackPrefs
import com.xiaowei.player.data.db.AppDatabase
import com.xiaowei.player.player.MusicPlayerManager
import com.xiaowei.player.i18n.Strings

class ShuYinApp : Application(), ImageLoaderFactory {

    lateinit var database: AppDatabase
        private set
    lateinit var playerManager: MusicPlayerManager
        private set
    lateinit var favoriteRepo: FavoriteRepository
        private set
    lateinit var playbackPrefs: PlaybackPrefs
        private set

    private val musicRepository by lazy { MusicRepository(this) }

    var onNotificationToggleFavorite: ((Long) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        cleanupLegacyCoverCache()

        database = AppDatabase.get(this)
        favoriteRepo = FavoriteRepository(database)
        playbackPrefs = PlaybackPrefs(database)
        playerManager = MusicPlayerManager(
            context = this,
            playbackPrefs = playbackPrefs,
            lyricsLoader = { song -> musicRepository.reloadLyrics(song) }
        )
        createNotificationChannel()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .respectCacheHeaders(false)
        .build()

    private fun cleanupLegacyCoverCache() {
        try {
            listOf("embedded_covers", "coil_cache", "image_cache").forEach { dir ->
                val legacy = File(cacheDir, dir)
                if (legacy.exists()) {
                    legacy.deleteRecursively()
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "shuyin_playback",
                Strings.get("notification_channel_name"),
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: ShuYinApp
            private set
    }
}
