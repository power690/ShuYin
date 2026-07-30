package com.xiaowei.player.player

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.xiaowei.player.data.AudioMixPrefs
import com.xiaowei.player.data.PlaybackPrefs
import com.xiaowei.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MusicPlayerManager(
    private val context: Context,
    private val playbackPrefs: PlaybackPrefs? = null,
    
    private val lyricsLoader: (suspend (Song) -> String?)? = null
) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var lyricsReloadJob: Job? = null

    
    private fun tryReloadLyricsIfNeeded(song: Song) {
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        
        if (song.hasLyrics) return
        
        if (!android.os.Environment.isExternalStorageManager()) return
        
        val loader = lyricsLoader ?: return

        lyricsReloadJob?.cancel()
        lyricsReloadJob = scope.launch {
            
            delay(200)
            val lyrics = try { loader(song) } catch (_: Exception) { null }
            if (lyrics.isNullOrBlank()) return@launch
            
            val current = _state.value.currentSong ?: return@launch
            if (current.id != song.id) return@launch
            
            _state.update { s ->
                val cur = s.currentSong
                if (cur?.id == song.id) s.copy(currentSong = cur.copy(lyrics = lyrics))
                else s
            }
            
            val idx = playlist.indexOfFirst { it.id == song.id }
            if (idx in playlist.indices) {
                playlist = playlist.toMutableList().also { l ->
                    l[idx] = l[idx].copy(lyrics = lyrics)
                }
            }
        }
    }

    val player: ExoPlayer by lazy {

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableAudioTrackPlaybackParams(true)

        val mediaAudioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(
                mediaAudioAttributes,
                !AudioMixPrefs.get(context).mixWithOthers
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
                    if (playbackState == Player.STATE_ENDED) {

                        autoAdvanceToNext()
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {

                    _state.update { it.copy(isPlaying = player.playWhenReady) }
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val idx = currentMediaItemIndex
                    _state.update { s ->
                        s.copy(
                            currentSong = playlist.getOrNull(idx),
                            currentIndex = idx
                        )
                    }

                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        savePlaybackStateSnapshot(positionMs = player.currentPosition)
                    }

                    
                    playlist.getOrNull(idx)?.let { tryReloadLyricsIfNeeded(it) }
                }
            })
        }
    }

    val mediaSession: MediaSessionCompat by lazy {
        MediaSessionCompat(context, "ShuYinMusicSession").apply {
            isActive = true
        }
    }

    var playlist: List<Song> = emptyList()
        private set

    data class PlayerState(
        val currentSong: Song? = null,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val positionUpdateNanos: Long = 0L,

        val playMode: PlayMode = PlayMode.SEQUENCE,

        val currentIndex: Int = -1
    )

    enum class PlayMode { SEQUENCE, SHUFFLE, REPEAT_ONE }

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var tickerJob: Job? = null

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        playlist = songs

        player.setMediaItems(
            songs.map { it.toMediaItem() },
            startIndex.coerceIn(0, songs.lastIndex),
            0L
        )
        player.prepare()
        player.playWhenReady = true
        _state.update {
            it.copy(
                currentSong = songs[startIndex],
                currentIndex = startIndex
            )
        }
        startTicker()
        
        tryReloadLyricsIfNeeded(songs[startIndex])
    }

    fun playAtIndex(index: Int) {
        if (index < 0 || index >= playlist.size) return
        player.seekToDefaultPosition(index)
        player.prepare()
        player.playWhenReady = true
        _state.update {
            it.copy(
                currentSong = playlist[index],
                currentIndex = index
            )
        }
        startTicker()
        
        tryReloadLyricsIfNeeded(playlist[index])
    }

    fun removeFromQueue(index: Int) {
        if (index < 0 || index >= playlist.size) return
        val current = _state.value.currentIndex
        val isRemovingCurrent = index == current

        player.removeMediaItem(index)

        playlist = playlist.toMutableList().apply { removeAt(index) }

        if (playlist.isEmpty()) {
            _state.update { it.copy(currentSong = null, currentIndex = -1) }
            return
        }

        val newIndex = when {
            isRemovingCurrent -> current.coerceAtMost(playlist.size - 1)
            index < current -> current - 1
            else -> current
        }
        _state.update {
            it.copy(
                currentSong = playlist.getOrNull(newIndex),
                currentIndex = newIndex
            )
        }

        if (isRemovingCurrent) {
            player.seekToDefaultPosition(newIndex)
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun clearQueue() {
        player.clearMediaItems()
        player.stop()
        playlist = emptyList()
        _state.update { it.copy(currentSong = null, currentIndex = -1, isPlaying = false) }
    }

    fun togglePlayPause() {

        if (player.playWhenReady) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun skipToNext() {
        if (playlist.isEmpty()) return
        val current = _state.value.currentIndex
        if (current < 0) return
        val nextIndex = if (current + 1 < playlist.size) current + 1 else 0
        playAtIndexInternal(nextIndex)
    }

    fun skipToPrevious() {
        if (playlist.isEmpty()) return
        val current = _state.value.currentIndex
        if (current < 0) return
        val prevIndex = if (current - 1 >= 0) current - 1 else playlist.size - 1
        playAtIndexInternal(prevIndex)
    }

    private fun autoAdvanceToNext() {
        if (playlist.isEmpty()) return
        val current = _state.value.currentIndex
        if (current < 0) return
        when (_state.value.playMode) {
            PlayMode.SHUFFLE -> {

                val nextIndex = if (playlist.size > 1) {
                    var random: Int
                    do {
                        random = (0 until playlist.size).random()
                    } while (random == current)
                    random
                } else 0
                playAtIndexInternal(nextIndex)
            }
            PlayMode.REPEAT_ONE -> {

                player.seekTo(0)
                player.playWhenReady = true
            }
            PlayMode.SEQUENCE -> {

                val nextIndex = if (current + 1 < playlist.size) current + 1 else 0
                playAtIndexInternal(nextIndex)
            }
        }
    }

    private fun playAtIndexInternal(index: Int) {
        if (index < 0 || index >= playlist.size) return
        player.seekTo(index, 0)
        player.playWhenReady = true
        _state.update {
            it.copy(
                currentSong = playlist[index],
                currentIndex = index
            )
        }
        
        tryReloadLyricsIfNeeded(playlist[index])
    }

    fun cyclePlayMode() {
        val next = when (_state.value.playMode) {
            PlayMode.SEQUENCE -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.SEQUENCE
        }
        applyPlayMode(next)
        val msgKey = when (next) {
            PlayMode.SEQUENCE -> "play_mode_sequence"
            PlayMode.SHUFFLE -> "play_mode_shuffle"
            PlayMode.REPEAT_ONE -> "play_mode_repeat_one"
        }
        android.widget.Toast.makeText(
            context,
            com.xiaowei.player.i18n.Strings.get(msgKey),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun applyPlayMode(mode: PlayMode) {
        when (mode) {
            PlayMode.SEQUENCE -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
            PlayMode.SHUFFLE -> {

                player.shuffleModeEnabled = true
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
            PlayMode.REPEAT_ONE -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_ONE
            }
        }
        _state.update { it.copy(playMode = mode) }
    }

    fun updateAudioFocusHandling(mixWithOthers: Boolean) {
        try {
            val attrs = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
            player.setAudioAttributes(attrs, !mixWithOthers)
        } catch (_: Exception) {
        }
    }

    fun release() {

        if (playlist.isNotEmpty()) {
            savePlaybackStateSnapshot(positionMs = player.currentPosition)
        }
        tickerJob?.cancel()
        player.release()
        mediaSession.release()
    }

    private fun savePlaybackStateSnapshot(positionMs: Long) {
        val prefs = playbackPrefs ?: return
        if (playlist.isEmpty()) {
            prefs.clearSync()
            return
        }
        prefs.saveSync(
            PlaybackPrefs.SavedState(
                songIds = playlist.map { it.id },
                currentIndex = _state.value.currentIndex.coerceIn(0, playlist.lastIndex),
                positionMs = positionMs.coerceAtLeast(0L),
                playModeName = _state.value.playMode.name
            )
        )
    }

    fun restoreFromPrefs(allSongs: List<Song>): Boolean {
        val prefs = playbackPrefs ?: return false
        val saved = prefs.loadSync() ?: return false
        if (saved.songIds.isEmpty()) return false

        val songMap = allSongs.associateBy { it.id }
        val restoredPlaylist = saved.songIds.mapNotNull { songMap[it] }
        if (restoredPlaylist.isEmpty()) {

            prefs.clearSync()
            return false
        }

        val savedIndexId = saved.songIds.getOrNull(saved.currentIndex.coerceIn(0, saved.songIds.lastIndex))
        val restoredIndex = if (savedIndexId != null) {
            restoredPlaylist.indexOfFirst { it.id == savedIndexId }
        } else 0
        val safeIndex = restoredIndex.coerceAtLeast(0).coerceAtMost(restoredPlaylist.lastIndex)

        val restoredMode = runCatching {
            PlayMode.valueOf(saved.playModeName)
        }.getOrDefault(PlayMode.SEQUENCE)

        playlist = restoredPlaylist
        player.setMediaItems(
            restoredPlaylist.map { it.toMediaItem() },
            safeIndex,
            saved.positionMs.coerceAtLeast(0L)
        )

        player.prepare()
        player.playWhenReady = false
        applyPlayMode(restoredMode)

        _state.update {
            it.copy(
                currentSong = restoredPlaylist.getOrNull(safeIndex),
                currentIndex = safeIndex,
                positionMs = saved.positionMs.coerceAtLeast(0L),
                playMode = restoredMode,
                isPlaying = false
            )
        }
        startTicker()
        return true
    }

    private fun startPlaybackService() {
        PlaybackService.startService(context)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var saveCounter = 0
            while (true) {
                val pos = player.currentPosition.coerceAtLeast(0)
                val dur = player.duration.coerceAtLeast(0)
                val nowNanos = System.nanoTime()
                _state.update { it.copy(positionMs = pos, durationMs = dur, positionUpdateNanos = nowNanos) }

                saveCounter++
                if (saveCounter >= 40 && playlist.isNotEmpty()) {
                    saveCounter = 0
                    savePlaybackStateSnapshot(positionMs = pos)
                }
                delay(50)
            }
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(displayArtist)
            .setAlbumTitle(displayAlbum)
            .setAlbumArtist(albumArtist ?: displayArtist)
            .setArtworkUri(albumArtUri)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()

        val playUri = when (source) {
            "custom_path" -> {

                try {
                    Uri.fromFile(java.io.File(data))
                } catch (_: Exception) {
                    Uri.parse(data)
                }
            }
            else -> {

                if (id > 0) {
                    ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                } else {

                    try {
                        if (data.startsWith("content://") || data.startsWith("file://")) {
                            Uri.parse(data)
                        } else if (data.isNotBlank()) {
                            Uri.fromFile(java.io.File(data))
                        } else {
                            Uri.EMPTY
                        }
                    } catch (_: Exception) {
                        Uri.parse(data)
                    }
                }
            }
        }
        return MediaItem.Builder()
            .setUri(playUri)
            .setMediaId(id.toString())
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        private const val TAG = "MusicPlayerManager"
    }
}
