package com.xiaowei.player.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.xiaowei.player.MainActivity
import com.xiaowei.player.R
import com.xiaowei.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicNotificationManager(
    private val context: Context,
    private val mediaSession: MediaSessionCompat
) {

    companion object {
        private const val TAG = "MusicNotificationManager"
        const val CHANNEL_ID = "music_playback_channel"
        const val CHANNEL_NAME = "音乐播放"
        const val CHANNEL_DESC = "显示当前播放的音乐信息和控制"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PREV = "com.xiaowei.player.ACTION_PREV"
        const val ACTION_PLAY = "com.xiaowei.player.ACTION_PLAY"
        const val ACTION_NEXT = "com.xiaowei.player.ACTION_NEXT"
        const val ACTION_CLOSE = "com.xiaowei.player.ACTION_CLOSE"
        const val ACTION_CYCLE_PLAY_MODE = "com.xiaowei.player.ACTION_CYCLE_PLAY_MODE"
        const val ACTION_TOGGLE_FAVORITE = "com.xiaowei.player.ACTION_TOGGLE_FAVORITE"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var currentLargeIcon: Bitmap? = null
    private var lastLoadedArtUri: String? = null
    private var albumArtLoadJob: Job? = null

    var isAppInForeground: () -> Boolean = { true }

    private data class CachedNotificationParams(
        val song: Song?,
        val isPlaying: Boolean,
        val playMode: MusicPlayerManager.PlayMode,
        val isFavorite: Boolean = false
    )

    private var cachedParams: CachedNotificationParams? = null
    private var trackedSongId: Long = -1L
    private var lastSafeDuration: Long = 0L

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateMediaSessionPlaybackState(
        isPlaying: Boolean,
        currentPosition: Long,
        bufferedPosition: Long,
        duration: Long
    ) {
        try {
            if (duration > 0) {
                lastSafeDuration = duration
            }
            val safePosition = currentPosition.coerceAtLeast(0L)
            val displaySpeed = if (isPlaying) 1.0f else 0.0f
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED

            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, safePosition, displaySpeed, SystemClock.elapsedRealtime())
                .setBufferedPosition(bufferedPosition.coerceAtLeast(0L))
                .build()
            mediaSession.setPlaybackState(playbackState)

            if (lastSafeDuration > 0) {
                val currentMetadata = mediaSession.controller?.metadata
                val currentDuration = currentMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0
                if (currentDuration != lastSafeDuration) {
                    updateMediaSessionMetadataWithDuration(lastSafeDuration)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating playback state: ${e.message}")
        }
    }

    fun onSeekTo(position: Long) {
        if (position >= 0) {
            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, position, 1.0f, SystemClock.elapsedRealtime())
                .build()
            mediaSession.setPlaybackState(playbackState)
        }
    }

    fun buildNotification(
        song: Song?,
        isPlaying: Boolean,
        playMode: MusicPlayerManager.PlayMode,
        isFavorite: Boolean,
        currentPosition: Long,
        duration: Long
    ): Notification {
        val songId = song?.id ?: -1L
        if (songId != trackedSongId) {
            trackedSongId = songId
            lastSafeDuration = 0L
        }
        if (duration > 0) {
            lastSafeDuration = duration
        }
        val safeDuration = lastSafeDuration
        val safePosition = currentPosition.coerceAtLeast(0L)

        cachedParams = CachedNotificationParams(song, isPlaying, playMode, isFavorite)
        loadAlbumArt(song)

        val title = song?.title ?: com.xiaowei.player.i18n.Strings.get("music")
        val artist = song?.displayAlbumDashArtist ?: com.xiaowei.player.i18n.Strings.get("not_playing")
        val timeText = if (safeDuration > 0) "${formatTime(safePosition)} / ${formatTime(safeDuration)}" else ""

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (modeIcon, modeText) = when (playMode) {
            MusicPlayerManager.PlayMode.SEQUENCE -> R.drawable.ic_repeat to com.xiaowei.player.i18n.Strings.get("play_mode_sequence")
            MusicPlayerManager.PlayMode.SHUFFLE -> R.drawable.ic_shuffle to com.xiaowei.player.i18n.Strings.get("play_mode_shuffle")
            MusicPlayerManager.PlayMode.REPEAT_ONE -> R.drawable.ic_repeat_one to com.xiaowei.player.i18n.Strings.get("play_mode_repeat_one")
        }

        val (favIcon, favText) = if (isFavorite) {
            R.drawable.ic_heart_filled to com.xiaowei.player.i18n.Strings.get("unfavorite")
        } else {
            R.drawable.ic_heart to com.xiaowei.player.i18n.Strings.get("favorite")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(timeText)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(getServicePendingIntent(ACTION_CLOSE))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setShowWhen(false)
            .setLargeIcon(currentLargeIcon ?: createDefaultAlbumArt())
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)

                    .setShowActionsInCompactView(0, 1, 2, 3, 4)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(getServicePendingIntent(ACTION_CLOSE))
            )
            .apply {
                val durSec = (safeDuration / 1000).toInt().coerceAtLeast(0)
                val posSec = (safePosition / 1000).toInt().coerceAtLeast(0)
                if (durSec > 0) {
                    setProgress(durSec, posSec, false)
                } else {
                    setProgress(0, 0, true)
                }
            }

            .addAction(modeIcon, modeText, getServicePendingIntent(ACTION_CYCLE_PLAY_MODE))
            .addAction(R.drawable.ic_prev, com.xiaowei.player.i18n.Strings.get("previous"), getServicePendingIntent(ACTION_PREV))
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) com.xiaowei.player.i18n.Strings.get("pause") else com.xiaowei.player.i18n.Strings.get("play"),
                getServicePendingIntent(ACTION_PLAY)
            )
            .addAction(R.drawable.ic_next, com.xiaowei.player.i18n.Strings.get("next"), getServicePendingIntent(ACTION_NEXT))
            .addAction(favIcon, favText, getServicePendingIntent(ACTION_TOGGLE_FAVORITE))

        updateMediaSessionMetadata(song, safeDuration)
        return builder.build()
    }

    fun updateNotification(
        song: Song?,
        isPlaying: Boolean,
        playMode: MusicPlayerManager.PlayMode,
        isFavorite: Boolean,
        currentPosition: Long,
        duration: Long
    ) {
        val notification = buildNotification(song, isPlaying, playMode, isFavorite, currentPosition, duration)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun getServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun loadAlbumArt(song: Song?) {
        val songData = song?.data
        if (songData.isNullOrBlank()) {
            currentLargeIcon = createDefaultAlbumArt()
            lastLoadedArtUri = null
            return
        }

        if (songData == lastLoadedArtUri) return

        albumArtLoadJob?.cancel()
        albumArtLoadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val bytes = com.xiaowei.player.data.EmbeddedCoverFetcher.loadCoverBytes(songData)
                val bitmap = if (bytes != null && bytes.isNotEmpty() && isActive) {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) {
                            sample *= 2
                        }
                        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    } else null
                } else null

                if (bitmap != null && isActive) {
                    currentLargeIcon = bitmap
                    lastLoadedArtUri = songData
                    if (isAppInForeground()) return@launch
                    cachedParams?.let { params ->
                        val currentPos = try {
                            (mediaSession.controller?.playbackState?.position ?: 0L)
                                .coerceAtLeast(0L)
                        } catch (_: Exception) { 0L }
                        val notification = buildNotification(
                            params.song, params.isPlaying, params.playMode, params.isFavorite,
                            currentPos, lastSafeDuration
                        )
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    }
                } else if (isActive) {
                    currentLargeIcon = createDefaultAlbumArt()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Error loading album art: ${e.message}")
                currentLargeIcon = createDefaultAlbumArt()
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun createDefaultAlbumArt(): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(0xFFE0E0E0.toInt())
        val paint = android.graphics.Paint().apply {
            color = 0xFF9E9E9E.toInt()
            textSize = 48f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val fontMetrics = paint.fontMetrics
        val centerY = size / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText("♪", size / 2f, centerY, paint)
        return bitmap
    }

    private fun updateMediaSessionMetadata(song: Song?, duration: Long) {
        try {
            val metadata = MediaMetadataCompat.Builder().apply {
                song?.let {
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, it.title)
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it.displayArtist)
                    putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it.displayAlbum)
                    putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    currentLargeIcon?.let { bitmap ->
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    }
                }
            }.build()
            mediaSession.setMetadata(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating media session metadata: ${e.message}")
        }
    }

    private fun updateMediaSessionMetadataWithDuration(duration: Long) {
        try {
            val existingMetadata = mediaSession.controller?.metadata
            val metadata = MediaMetadataCompat.Builder().apply {
                existingMetadata?.let { meta ->
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                        meta.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "")
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                        meta.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "")
                    putString(MediaMetadataCompat.METADATA_KEY_ALBUM,
                        meta.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: "")
                    val art = meta.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                    if (art != null) {
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                    }
                }
                putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            }.build()
            mediaSession.setMetadata(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating media session metadata duration: ${e.message}")
        }
    }
}
