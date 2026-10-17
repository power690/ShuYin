package com.xiaowei.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.xiaowei.player.data.EmbeddedCoverFetcher
import com.xiaowei.player.data.FavoriteRepository
import com.xiaowei.player.data.LoginPlaybackGate
import com.xiaowei.player.data.MusicRepository
import com.xiaowei.player.data.PlaybackPrefs
import com.xiaowei.player.data.WebDavMusicSource
import com.xiaowei.player.data.WebDavPrefs
import com.xiaowei.player.data.db.AppDatabase
import com.xiaowei.player.player.MusicPlayerManager
import com.xiaowei.player.i18n.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
    private val webDavMusicSource by lazy { WebDavMusicSource(this) }

    var onNotificationToggleFavorite: ((Long) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.get(this)
        favoriteRepo = FavoriteRepository(database)
        playbackPrefs = PlaybackPrefs(database)
        playerManager = MusicPlayerManager(
            context = this,
            playbackPrefs = playbackPrefs,
            lyricsLoader = { song ->
                if (song.source == "webdav") {
                    val account = WebDavPrefs.get(this).activeAccount()
                    if (account != null) webDavMusicSource.fetchLyricsFor(account, song) else null
                } else {
                    musicRepository.reloadLyrics(song)
                }
            }
        )
        createNotificationChannel()
        purgeCoverCaches()
        registerLoginPlaybackGate()
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val loginPlaybackGate = LoginPlaybackGate(this, appScope)

    private fun registerLoginPlaybackGate() {
        var startedActivityCount = 0
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                startedActivityCount++
                loginPlaybackGate.onForegroundStateChanged(startedActivityCount > 0)
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivityCount--
                if (startedActivityCount < 0) startedActivityCount = 0
                loginPlaybackGate.onForegroundStateChanged(startedActivityCount > 0)
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}

            override fun onActivityResumed(activity: android.app.Activity) {}

            override fun onActivityPaused(activity: android.app.Activity) {}

            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}

            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
        appScope.launch {
            playerManager.state.collect { state ->
                loginPlaybackGate.onPlaybackStateChanged(state.isPlaying || state.isBuffering)
            }
        }
    }

    private fun purgeCoverCaches() {
        Thread {
            try {
                val coilCache = java.io.File(cacheDir, "coil_cache")
                if (coilCache.exists()) {
                    coilCache.deleteRecursively()
                }
            } catch (_: Throwable) {
            }
            EmbeddedCoverFetcher.purgeLegacyDiskCache(this)
        }.start()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.18)
                .build()
        }
        .respectCacheHeaders(false)
        .build()

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
