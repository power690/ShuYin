package com.xiaowei.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xiaowei.player.data.LyricsParser
import com.xiaowei.player.data.UpdateChecker
import com.xiaowei.player.player.DesktopLyricService
import com.xiaowei.player.player.PlaybackService
import com.xiaowei.player.ui.ShuYinApp
import com.xiaowei.player.ui.screens.UpdateCheckerHost
import com.xiaowei.player.ui.theme.ZMusicTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var floatingLyricEnabled by mutableStateOf(false)

    private var isSystemDarkTheme by mutableStateOf(false)

    private var cachedLyrics: List<DesktopLyricService.FloatingLyricLine> = emptyList()
    private var cachedLyricSongId: Long = -1L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        viewModel.onPermissionResult(granted)
        if (granted) checkNotificationPermission()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        
        checkAndRequestManageStoragePermission()
    }

    
    private var wasManageStorageGranted = false

    override fun attachBaseContext(newBase: Context) {
        val langCode = com.xiaowei.player.data.LocalePrefs.get(newBase).languageCode

        val locale = if (langCode != null) {

            val parts = langCode.split("-")
            if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
        } else {

            getSystemLocale()
        }

        Locale.setDefault(locale)

        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)

        config.setLayoutDirection(locale)
        val wrappedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(wrappedContext)
    }

    private fun getSystemLocale(): Locale {
        val systemConfig = android.content.res.Resources.getSystem().configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            systemConfig.locales[0]
        } else {

            @Suppress("DEPRECATION")
            systemConfig.locale ?: Locale.getDefault()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isSystemDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        com.xiaowei.player.data.LocalePrefs.get(this).languageCode?.let {
            com.xiaowei.player.i18n.Strings.setCurrentLanguage(it)
        }

        if (DesktopLyricService.isRunning()) {
            floatingLyricEnabled = true
        }

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wasManageStorageGranted = android.os.Environment.isExternalStorageManager()
        }
        checkPermissionAndLoad()

        setContent {
            ZMusicTheme(darkTheme = isSystemDarkTheme) {
                val state by viewModel.library.collectAsState()
                val playerState by viewModel.playerManager.state.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(
                    playerState.currentSong?.id,
                    playerState.positionMs / 50L,
                    playerState.isPlaying,
                    floatingLyricEnabled
                ) {
                    if (!floatingLyricEnabled) return@LaunchedEffect
                    pushLyricToService(playerState.currentSong, playerState.positionMs, playerState.positionUpdateNanos, playerState.isPlaying)
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        ShuYinApp(
                            library = state,
                            playerState = playerState,
                            playerPlaylist = viewModel.playerManager.playlist,
                            onPlaySong = { song, list -> viewModel.playSongFromList(list, song) },
                            onPlayAll = viewModel::playAll,
                            onRefresh = {

                                val customPath = com.xiaowei.player.data.CustomPathPrefs
                                    .get(this@MainActivity).path.trim()
                                if (customPath.isNotBlank()) {

                                    val hasManagePerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        android.os.Environment.isExternalStorageManager()
                                    } else {
                                        ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.READ_EXTERNAL_STORAGE
                                        ) == PackageManager.PERMISSION_GRANTED
                                    }
                                    if (hasManagePerm) {
                                        viewModel.refresh()
                                    } else {
                                        requestManageStoragePermission()
                                    }
                                } else {

                                    if (state.permissionGranted) viewModel.refresh()
                                    else permissionLauncher.launch(requiredPermissions())
                                }
                            },
                            onSearch = viewModel::setSearchQuery,
                            onTogglePlayPause = viewModel.playerManager::togglePlayPause,
                            onSkipNext = viewModel.playerManager::skipToNext,
                            onSkipPrev = viewModel.playerManager::skipToPrevious,
                            onSeek = viewModel.playerManager::seekTo,
                            onCyclePlayMode = viewModel.playerManager::cyclePlayMode,
                            onPlayAtIndex = viewModel.playerManager::playAtIndex,
                            onRemoveFromQueue = viewModel.playerManager::removeFromQueue,
                            onClearQueue = viewModel.playerManager::clearQueue,
                            onToggleFavorite = viewModel::toggleFavorite,
                            floatingLyricEnabled = floatingLyricEnabled,
                            onToggleFloatingLyric = { toggleFloatingLyric() },
                            onCustomPathConfirm = { path -> viewModel.refreshFromPath(path) }
                        )

                        UpdateCheckerHost(
                            onCheckRequested = { },
                            manualTrigger = null
                        )
                    }
                }
            }
        }
    }

    private fun toggleFloatingLyric() {
        if (floatingLyricEnabled) {
            DesktopLyricService.setCloseCallback(null)
            stopService(Intent(this, DesktopLyricService::class.java))
            floatingLyricEnabled = false
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
            return
        }
        startService(Intent(this, DesktopLyricService::class.java))
        floatingLyricEnabled = true
        DesktopLyricService.setCloseCallback { floatingLyricEnabled = false }
        val state = viewModel.playerManager.state.value
        pushLyricToService(state.currentSong, state.positionMs, state.positionUpdateNanos, state.isPlaying)
    }

    private fun pushLyricToService(currentSong: com.xiaowei.player.data.Song?, positionMs: Long, positionUpdateNanos: Long, isPlaying: Boolean) {
        if (!DesktopLyricService.isRunning()) {
            android.util.Log.d("KARAOKE_DBG", "pushLyric: service not running, skip")
            return
        }
        if (currentSong == null) {
            android.util.Log.d("KARAOKE_DBG", "pushLyric: currentSong null")
            DesktopLyricService.updateLyric(emptyList(), -1, positionMs, positionUpdateNanos, isPlaying)
            return
        }
        if (currentSong.id != cachedLyricSongId) {
            cachedLyricSongId = currentSong.id
            val rawLyricsLen = currentSong.lyrics?.length ?: 0
            val parsed = LyricsParser.parse(currentSong.lyrics)
            val wordsCount = parsed.sumOf { it.words.size }
            android.util.Log.d("KARAOKE_DBG", "pushLyric: new song id=${currentSong.id} title=${currentSong.title} rawLyricsLen=$rawLyricsLen parsedLines=${parsed.size} totalWords=$wordsCount")
            cachedLyrics = parsed.map {
                DesktopLyricService.FloatingLyricLine(
                    it.timeMs,
                    it.text,
                    it.words.map { w -> DesktopLyricService.FloatingLyricWord(w.timeMs, w.text) }
                )
            }
        }
        val idx = LyricsParser.findCurrentLine(
            cachedLyrics.map { com.xiaowei.player.data.LyricLine(it.timeMs, it.text) },
            positionMs
        )
        val curLine = if (idx in cachedLyrics.indices) cachedLyrics[idx] else null
        android.util.Log.d("KARAOKE_DBG", "pushLyric: pos=${positionMs}ms idx=$idx lineText='${curLine?.text?.take(20)}' words=${curLine?.words?.size ?: 0} isPlaying=$isPlaying")
        DesktopLyricService.updateLyric(cachedLyrics, idx, positionMs, positionUpdateNanos, isPlaying)
    }

    private fun checkPermissionAndLoad() {
        val perms = requiredPermissions()
        val allGranted = perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            viewModel.onPermissionResult(true)
            checkNotificationPermission()
        } else {
            permissionLauncher.launch(perms)
        }
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                ).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Exception) {

                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {

                    android.widget.Toast.makeText(
                        this,
                        com.xiaowei.player.i18n.Strings.get("system_not_supported"),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {

            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                
                checkAndRequestManageStoragePermission()
            }
        } else {

            val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
            if (!enabled) {
                openAppNotificationSettings()
                
            } else {
                
                checkAndRequestManageStoragePermission()
            }
        }
    }

    
    private fun checkAndRequestManageStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (android.os.Environment.isExternalStorageManager()) {
            wasManageStorageGranted = true
            return
        }
        requestManageStoragePermission()
    }

    private fun openAppNotificationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            }
            startActivity(intent)
        } catch (_: Exception) {

            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isSystemDarkTheme = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onStop() {
        super.onStop()

        if (isChangingConfigurations) return
        val state = viewModel.playerManager.state.value
        if (state.currentSong != null && state.isPlaying) {
            PlaybackService.startService(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (floatingLyricEnabled && !DesktopLyricService.isRunning()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startService(Intent(this, DesktopLyricService::class.java))
            } else {
                floatingLyricEnabled = false
            }
        }
        
        
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wasManageStorageGranted = android.os.Environment.isExternalStorageManager()
        }
    }
}
