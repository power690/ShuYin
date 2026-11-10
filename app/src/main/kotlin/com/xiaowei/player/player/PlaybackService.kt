package com.xiaowei.player.player

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.xiaowei.player.ShuYinApp
import com.xiaowei.player.data.LyricLine
import com.xiaowei.player.data.LyricsParser

class PlaybackService : Service() {

    companion object {
        private const val TAG = "PlaybackService"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        private const val PAUSE_DISMISS_DELAY_MS = 120_000L

        @Volatile
        var isRunning = false
            private set

        const val ACTION_PLAY = "com.xiaowei.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.xiaowei.player.ACTION_PAUSE"
        const val ACTION_NEXT = "com.xiaowei.player.ACTION_NEXT"
        const val ACTION_PREV = "com.xiaowei.player.ACTION_PREV"
        const val ACTION_CLOSE = "com.xiaowei.player.ACTION_CLOSE"
        const val ACTION_CYCLE_PLAY_MODE = "com.xiaowei.player.ACTION_CYCLE_PLAY_MODE"
        const val ACTION_TOGGLE_FAVORITE = "com.xiaowei.player.ACTION_TOGGLE_FAVORITE"

        private fun startServiceForeground(service: Service, id: Int, notification: Notification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                service.startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                service.startForeground(id, notification)
            }
        }

        fun startService(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }

    private var isAppInForeground = false
    private lateinit var playerManager: MusicPlayerManager
    private lateinit var notificationManager: MusicNotificationManager

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) { handleAppForeground() }
        override fun onPause(owner: LifecycleOwner) { handleAppBackground() }
    }

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updatePeriodicProgress()
            progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    private var lastNotificationSongId: Long = -1L
    private var lyricSongId: Long = -1L
    private var lyricRawText: String? = null
    private var lyricLines: List<LyricLine> = emptyList()
    private var lastPostedStatusLyric: String? = null
    private var lastPostedIsPlaying: Boolean = false
    private var lastPostedPlayMode: MusicPlayerManager.PlayMode = MusicPlayerManager.PlayMode.SEQUENCE
    private var lastPostedIsFavorite: Boolean = false
    private var notificationCurrentlyPosted: Boolean = false

    private fun rememberPostedNotification(
        songId: Long,
        isPlaying: Boolean,
        playMode: MusicPlayerManager.PlayMode,
        isFavorite: Boolean,
        lyricText: String?
    ) {
        lastNotificationSongId = songId
        lastPostedStatusLyric = lyricText
        lastPostedIsPlaying = isPlaying
        lastPostedPlayMode = playMode
        lastPostedIsFavorite = isFavorite
        notificationCurrentlyPosted = true
    }

    private fun currentStatusLyricText(position: Long): String? {
        if (!::playerManager.isInitialized) return null
        val song = playerManager.state.value.currentSong ?: return null
        val raw = song.lyrics
        if (raw.isNullOrBlank()) {
            lyricLines = emptyList()
            return song.title
        }
        if (song.id != lyricSongId || raw != lyricRawText) {
            lyricSongId = song.id
            lyricRawText = raw
            lyricLines = LyricsParser.parse(raw)
        }
        if (lyricLines.isEmpty()) return song.title
        val idx = LyricsParser.findCurrentLine(lyricLines, position)
        if (idx < 0) return song.title
        val text = lyricLines.getOrNull(idx)?.text?.trim().orEmpty()
        return text.ifBlank { song.title }
    }

    private val pauseDismissHandler = Handler(Looper.getMainLooper())
    private var pauseDismissScheduled = false
    private val pauseDismissRunnable = Runnable {
        if (::playerManager.isInitialized && !playerManager.state.value.isPlaying) {
            removeNotificationAndStop()
        }
    }

    private fun schedulePauseDismiss() {
        if (pauseDismissScheduled) return
        pauseDismissScheduled = true
        pauseDismissHandler.postDelayed(pauseDismissRunnable, PAUSE_DISMISS_DELAY_MS)
    }

    private fun cancelPauseDismiss() {
        if (!pauseDismissScheduled) return
        pauseDismissScheduled = false
        pauseDismissHandler.removeCallbacks(pauseDismissRunnable)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        isRunning = true

        createNotificationChannel()

        val placeholder = buildPlaceholderNotification()
        startServiceForeground(this, MusicNotificationManager.NOTIFICATION_ID, placeholder)
        notificationCurrentlyPosted = true

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        isAppInForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

        val app = applicationContext as ShuYinApp
        playerManager = app.playerManager
        notificationManager = MusicNotificationManager(this, playerManager.mediaSession)
        notificationManager.notificationActive = { notificationCurrentlyPosted }

        setupMediaSessionCallback()

        updateNotificationIfNeeded()

        progressHandler.post(progressRunnable)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                MusicNotificationManager.CHANNEL_ID,
                MusicNotificationManager.CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = MusicNotificationManager.CHANNEL_DESC
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun shouldShowNotification(): Boolean {
        if (!::playerManager.isInitialized) return false
        playerManager.state.value.currentSong ?: return false
        if (playerManager.state.value.isPlaying) {
            cancelPauseDismiss()
            return true
        }
        if (isAppInForeground) {
            cancelPauseDismiss()
            return false
        }
        schedulePauseDismiss()
        return true
    }

    private fun handleAppForeground() {
        if (isAppInForeground) return
        isAppInForeground = true

        Log.d(TAG, "App → foreground — detaching notification")

        updateNotificationIfNeeded()
    }

    private fun handleAppBackground() {
        if (!isAppInForeground) return
        isAppInForeground = false
        Log.d(TAG, "App → background — evaluating notification")

        updateNotificationIfNeeded()

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isAppInForeground) {
                updateNotificationIfNeeded()
            }
        }, 200)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.action?.let { action ->
            if (action == ACTION_CLOSE) {
                handleAction(ACTION_CLOSE)
                return START_NOT_STICKY
            }
            handleAction(action)
        }

        try {
            if (!::notificationManager.isInitialized) {

                startServiceForeground(this, MusicNotificationManager.NOTIFICATION_ID, buildPlaceholderNotification())
            }

        } catch (e: Exception) {

            try {
                startServiceForeground(this, MusicNotificationManager.NOTIFICATION_ID, buildPlaceholderNotification())
            } catch (_: Exception) {}
        }

        updateNotificationIfNeeded()

        if (!shouldShowNotification() &&
            (::playerManager.isInitialized && playerManager.state.value.currentSong == null)) {
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun handleAction(action: String) {
        when (action) {
            ACTION_PLAY -> { playerManager.togglePlayPause() }
            ACTION_PAUSE -> { playerManager.togglePlayPause() }
            ACTION_NEXT -> { playerManager.skipToNext() }
            ACTION_PREV -> { playerManager.skipToPrevious() }
            ACTION_CYCLE_PLAY_MODE -> {
                playerManager.cyclePlayMode()
            }
            ACTION_TOGGLE_FAVORITE -> {
                val song = playerManager.state.value.currentSong
                if (song != null) {
                    val app = applicationContext as ShuYinApp
                    app.onNotificationToggleFavorite?.invoke(song.id)
                }
            }
            ACTION_CLOSE -> {
                if (playerManager.player.playWhenReady) playerManager.player.pause()
                stopPeriodicUpdates()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION") stopForeground(true)
                    }
                } catch (_: Exception) {}
                if (::notificationManager.isInitialized) {
                    notificationManager.cancelNotification()
                }
                stopSelf()
            }
            else -> Log.w(TAG, "Unknown action: $action")
        }
    }

    private fun setupMediaSessionCallback() {
        playerManager.mediaSession.setCallback(object : android.support.v4.media.session.MediaSessionCompat.Callback() {
            override fun onPlay() { if (!isRunning) try { PlaybackService.startService(applicationContext) } catch (_: Exception) {} ; playerManager.togglePlayPause(); updateNotificationIfNeeded() }
            override fun onPause() { playerManager.togglePlayPause(); updateNotificationIfNeeded() }
            override fun onSkipToNext() { if (!isRunning) try { PlaybackService.startService(applicationContext) } catch (_: Exception) {} ; playerManager.skipToNext(); updateNotificationIfNeeded() }
            override fun onSkipToPrevious() { if (!isRunning) try { PlaybackService.startService(applicationContext) } catch (_: Exception) {} ; playerManager.skipToPrevious(); updateNotificationIfNeeded() }
            override fun onSeekTo(pos: Long) {
                playerManager.seekTo(pos)
                notificationManager.onSeekTo(pos, playerManager.state.value.isPlaying)
            }
        })
    }

    private fun currentFavoriteState(songId: Long): Boolean {
        return try {

            (applicationContext as ShuYinApp).favoriteRepo.isFavoriteSync(songId)
        } catch (_: Exception) {
            false
        }
    }

    private fun postForegroundNotification(notification: Notification) {
        if (notificationCurrentlyPosted) {
            notificationManager.postNotification(notification)
        } else {
            startServiceForeground(this, MusicNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    private fun updatePeriodicProgress() {
        try {
            val song = playerManager.state.value.currentSong
            if (song == null) {
                if (shouldShowNotification()) {

                } else {
                    removeNotificationAndStop()
                    return
                }
            }

            val isPlaying = playerManager.state.value.isPlaying
            val position = playerManager.player.currentPosition
            val dur = playerManager.player.duration.coerceAtLeast(0L)
            val buffered = playerManager.player.bufferedPosition

            notificationManager.updateMediaSessionPlaybackState(
                isPlaying = isPlaying,
                currentPosition = position,
                bufferedPosition = buffered,
                duration = dur
            )

            if (!shouldShowNotification()) {
                removeNotificationAndStop()
                return
            }

            val playMode = playerManager.state.value.playMode
            val isFavorite = currentFavoriteState(song!!.id)
            val songChanged = song.id != lastNotificationSongId

            if (songChanged) {
                val statusText = currentStatusLyricText(position)
                val notification = notificationManager.buildNotification(
                    song = song,
                    isPlaying = isPlaying,
                    playMode = playMode,
                    isFavorite = isFavorite,
                    currentPosition = position,
                    duration = dur,
                    lyricText = statusText
                )
                postForegroundNotification(notification)
                rememberPostedNotification(song.id, isPlaying, playMode, isFavorite, statusText)
            } else {
                val statusText = currentStatusLyricText(position)
                if (statusText != lastPostedStatusLyric ||
                    isPlaying != lastPostedIsPlaying ||
                    playMode != lastPostedPlayMode ||
                    isFavorite != lastPostedIsFavorite
                ) {
                    notificationManager.updateNotification(
                        song = song,
                        isPlaying = isPlaying,
                        playMode = playMode,
                        isFavorite = isFavorite,
                        currentPosition = position,
                        duration = dur,
                        lyricText = statusText
                    )
                    rememberPostedNotification(song.id, isPlaying, playMode, isFavorite, statusText)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in periodic progress update: ${e.message}")
        }
    }

    private fun updateNotificationIfNeeded() {
        try {
            if (!::playerManager.isInitialized) return

            val song = playerManager.state.value.currentSong
            val isPlaying = playerManager.state.value.isPlaying
            val position = if (song != null) playerManager.player.currentPosition else 0L
            val dur = if (song != null) playerManager.player.duration.coerceAtLeast(0L) else 0L
            val buffered = if (song != null) playerManager.player.bufferedPosition else 0L

            notificationManager.updateMediaSessionPlaybackState(
                isPlaying = isPlaying,
                currentPosition = position,
                bufferedPosition = buffered,
                duration = dur
            )

            if (shouldShowNotification()) {
                val statusText = currentStatusLyricText(position)
                val playMode = playerManager.state.value.playMode
                val isFavorite = currentFavoriteState(song!!.id)
                val changed = song.id != lastNotificationSongId ||
                    statusText != lastPostedStatusLyric ||
                    isPlaying != lastPostedIsPlaying ||
                    playMode != lastPostedPlayMode ||
                    isFavorite != lastPostedIsFavorite
                if (!notificationCurrentlyPosted || changed) {
                    val notification = notificationManager.buildNotification(
                        song = song,
                        isPlaying = isPlaying,
                        playMode = playMode,
                        isFavorite = isFavorite,
                        currentPosition = position,
                        duration = dur,
                        lyricText = statusText
                    )
                    postForegroundNotification(notification)
                    rememberPostedNotification(song.id, isPlaying, playMode, isFavorite, statusText)
                }
            } else {
                removeNotificationAndStop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification: ${e.message}")
        }
    }

    private fun removeNotificationOnly() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
        } catch (_: Exception) {}
        if (::notificationManager.isInitialized) {
            notificationManager.cancelNotification()
        }
        notificationCurrentlyPosted = false
    }

    private fun removeNotificationAndStop() {
        removeNotificationOnly()
        stopSelf()
    }

    private fun stopPeriodicUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
    }

    private fun buildPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(this, MusicNotificationManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(com.xiaowei.player.i18n.Strings.get("app_name"))
            .setContentText(com.xiaowei.player.i18n.Strings.get("notification_preparing"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        cancelPauseDismiss()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        stopPeriodicUpdates()
        Log.d(TAG, "Service destroyed")
    }
}
