package com.xiaowei.player.player

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.Layout
import com.xiaowei.player.data.LyricsParser
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.xiaowei.player.ui.theme.DEFAULT_THEME_COLOR_INDEX
import com.xiaowei.player.ui.theme.PRESET_THEME_COLORS
import com.xiaowei.player.ui.theme.rememberZMusicColorScheme

class DesktopLyricService : Service() {

    data class FloatingLyricWord(val timeMs: Long, val text: String)
    data class FloatingLyricLine(
        val timeMs: Long,
        val text: String,
        val words: List<FloatingLyricWord> = emptyList()
    )

    private class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        fun dispatchEvent(event: Lifecycle.Event) { lifecycleRegistry.handleLifecycleEvent(event) }
        fun performRestore(savedState: Bundle?) { savedStateRegistryController.performRestore(savedState) }
    }

    companion object {
        private const val TAG = "DesktopLyricService"

        private var currentLyrics = mutableListOf<FloatingLyricLine>()
        private var currentLineIndex = 0
        private var currentPositionMs = 0L
        private var currentPositionUpdateNanos = 0L
        private var isPlaying = false

        private var instance: DesktopLyricService? = null

        private var pendingUpdate = false

        fun updateLyric(lyrics: List<FloatingLyricLine>, lineIndex: Int, positionMs: Long = 0L, positionUpdateNanos: Long = 0L, playing: Boolean = true) {
            currentLyrics.clear()
            currentLyrics.addAll(lyrics)
            currentLineIndex = lineIndex
            currentPositionMs = positionMs
            currentPositionUpdateNanos = positionUpdateNanos
            isPlaying = playing
            pendingUpdate = true
            val wordsCount = lyrics.sumOf { it.words.size }
            android.util.Log.d("KARAOKE_DBG", "updateLyric: lines=${lyrics.size} idx=$lineIndex pos=${positionMs}ms posUpdateNanos=${positionUpdateNanos} playing=$playing totalWords=$wordsCount instance=${if (instance != null) "yes" else "no"}")

            if (instance != null) {
                instance?.updateLyricView()
                pendingUpdate = false
            }
        }

        fun updatePlayingState(playing: Boolean) {
            isPlaying = playing
        }

        fun isRunning(): Boolean = instance != null

        fun updateFontSize(size: Float) {
            instance?.let {
                it.currentFontSize = size
                it.lyricTextView.textSize = size
                it.updateLyricMaxWidth()
            }
        }

        fun updateTextColor(@ColorInt color: Int) {
            instance?.let {
                it.followTheme = false
                it.currentTextColor = color
                it.lyricTextView.setTextColor(color)
            }
        }

        fun updateTextFollowTheme() {
            instance?.let {
                it.followTheme = true
                it.applyThemeColor()
            }
        }

        fun updateLockState(locked: Boolean) {
            instance?.let { it.isLocked = locked }
        }

        private var closeCallback: (() -> Unit)? = null

        fun setCloseCallback(cb: (() -> Unit)?) {
            closeCallback = cb
        }

        fun closeFloatingLyric() {
            instance?.let {
                it.stopKaraokeTick()
                it.hideSettings()
                closeCallback?.invoke()
                closeCallback = null
                it.stopSelf()
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var rootLayout: FrameLayout
    private lateinit var lyricLayout: LinearLayout
    private lateinit var lyricTextView: KaraokeTextView

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var screenWidth = 0
    private var screenHeight = 0

    private var displayedLyric = ""
    private var displayedWordSig = -1L

    private var isLocked = false
    private var currentFontSize = 20f
    @ColorInt private var currentTextColor = Color.WHITE
    private var followTheme = false
    private var themeColorCallback: (() -> Unit)? = null

    private var lastTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 300

    private var settingsComposeView: ComposeView? = null
    private var settingsMenuParams: WindowManager.LayoutParams? = null
    private var settingsLifecycleOwner: ServiceLifecycleOwner? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        loadSettings()
        themeColorCallback = {
            if (followTheme) applyThemeColor()
        }
        themeColorCallback?.let {
            com.xiaowei.player.data.ThemePrefs.get(this).addColorChangedListener(it)
        }
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            createLyricWindow()
            if (pendingUpdate) {
                updateLyricView()
                pendingUpdate = false
            } else {
                updateLyricView()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error creating service", e)
        }
    }

    private fun loadSettings() {
        val settings = DesktopLyricSettings.getSettings(this)
        currentFontSize = settings.fontSize
        followTheme = settings.textColor == DesktopLyricSettings.THEME_COLOR
        currentTextColor = if (followTheme) currentThemePrimaryColor() else settings.textColor
        isLocked = settings.enabled
    }

    private fun currentThemePrimaryColor(): Int {
        val themePrefs = com.xiaowei.player.data.ThemePrefs.get(this)
        val isDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        if (themePrefs.dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme = if (isDark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            return scheme.primary.toArgb()
        }
        val preset = PRESET_THEME_COLORS.getOrElse(themePrefs.themeColorIndex) {
            PRESET_THEME_COLORS[DEFAULT_THEME_COLOR_INDEX]
        }
        val primary = if (isDark) preset.darkPrimary else preset.lightPrimary
        return primary.toArgb()
    }

    private fun applyThemeColor() {
        currentTextColor = currentThemePrimaryColor()
        lyricTextView.setTextColor(currentTextColor)
        lyricTextView.invalidate()
    }

    @SuppressLint("InflateParams")
    private fun createLyricWindow() {
        try {
            val windowSize = Point()
            windowManager.defaultDisplay.getRealSize(windowSize)
            screenWidth = windowSize.x
            screenHeight = windowSize.y

            val savedSettings = DesktopLyricSettings.getSettings(this)

            layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                val halfW = screenWidth / 2
                x = savedSettings.positionX.coerceIn(-halfW, halfW)
                y = savedSettings.positionY.coerceAtLeast(0)
                alpha = 1f
            }

            val lifecycleOwner = ServiceLifecycleOwner()
            lifecycleOwner.performRestore(null)
            lifecycleOwner.dispatchEvent(Lifecycle.Event.ON_CREATE)
            settingsLifecycleOwner = lifecycleOwner

            rootLayout = FrameLayout(this).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            }

            lifecycleOwner.dispatchEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.dispatchEvent(Lifecycle.Event.ON_RESUME)

            lyricLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                lyricTextView = KaraokeTextView(context).apply {
                    textSize = currentFontSize
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                lyricTextView.setTextColor(currentTextColor)
                addView(lyricTextView)
            }
            rootLayout.addView(lyricLayout)
            rootLayout.setOnTouchListener { _, event -> handleTouchEvent(event) }
            windowManager.addView(rootLayout, layoutParams)
            updateLyricMaxWidth()
        } catch (e: SecurityException) {
            Log.e(TAG, "createLyricWindow: SecurityException", e)
        } catch (e: Exception) {
            Log.e(TAG, "createLyricWindow: Error", e)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun updateLyricMaxWidth() {
        val padding = dpToPx(16)
        val absX = Math.abs(layoutParams.x)
        val avail = (screenWidth - 2 * absX - padding * 2).coerceAtLeast(padding * 2)
        lyricTextView.maxWidth = avail
        lyricTextView.requestLayout()
        lyricTextView.invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isLocked) {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isLocked) {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isDragging = true
                        val newX = initialX + deltaX
                        val newY = initialY + deltaY
                        val halfW = screenWidth / 2
                        val viewH = rootLayout.height
                        layoutParams.x = newX.coerceIn(-halfW, halfW)
                        layoutParams.y = newY.coerceIn(0, (screenHeight - viewH).coerceAtLeast(0))
                        windowManager.updateViewLayout(rootLayout, layoutParams)
                        updateLyricMaxWidth()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    DesktopLyricSettings.savePosition(this, layoutParams.x, layoutParams.y)
                    return true
                }
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < DOUBLE_TAP_THRESHOLD) {
                    showSettings()
                    lastTapTime = 0
                    return true
                }
                lastTapTime = currentTime
                return false
            }
        }
        return false
    }

    private fun showSettings() {
        if (settingsComposeView != null) {
            hideSettings()
            return
        }
        try {
            val loc = IntArray(2)
            rootLayout.getLocationOnScreen(loc)
            val menuWidth = dpToPx(320)
            val menuX = (loc[0] + rootLayout.width / 2 - menuWidth / 2)
                .coerceIn(dpToPx(8), (screenWidth - menuWidth - dpToPx(8)).coerceAtLeast(dpToPx(8)))
            val menuY = (loc[1] + rootLayout.height + dpToPx(8))
                .coerceAtMost((screenHeight - dpToPx(16)).coerceAtLeast(0))

            val composeView = ComposeView(this)
            settingsLifecycleOwner?.let {
                composeView.setViewTreeLifecycleOwner(it)
                composeView.setViewTreeSavedStateRegistryOwner(it)
            }
            composeView.setContent {
                val configuration = LocalConfiguration.current
                val isDark = android.content.res.Configuration.UI_MODE_NIGHT_YES ==
                    configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK

                val themePrefs = com.xiaowei.player.data.ThemePrefs.get(this@DesktopLyricService)
                val colorScheme = rememberZMusicColorScheme(
                    darkTheme = isDark,
                    dynamicColorEnabled = themePrefs.dynamicColorEnabled,
                    themeColorIndex = themePrefs.themeColorIndex
                )
                MaterialTheme(colorScheme = colorScheme) {
                    Box(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures { _ -> hideSettings() }
                            }
                    ) {
                        LyricSettingsPopupContent(
                            onDismiss = { hideSettings() },
                            onCloseLyric = { closeFloatingLyric() }
                        )
                    }
                }
            }

            composeView.setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) {
                    hideSettings()
                    true
                } else {
                    false
                }
            }
            val menuParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.START
                x = menuX
                y = menuY
            }
            windowManager.addView(composeView, menuParams)
            settingsComposeView = composeView
            settingsMenuParams = menuParams
            composeView.post {
                val h = composeView.height
                if (h > 0 && menuY + h > screenHeight) {
                    menuParams.y = (screenHeight - h - dpToPx(16)).coerceAtLeast(0)
                    try {
                        windowManager.updateViewLayout(composeView, menuParams)
                    } catch (e: Exception) {
                        Log.e(TAG, "showSettings: adjust", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showSettings: Error", e)
        }
    }

    private fun hideSettings() {
        settingsComposeView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { Log.e(TAG, "hideSettings", e) }
        }
        settingsComposeView = null
        settingsMenuParams = null
    }

    @Composable
    private fun LyricSettingsPopupContent(onDismiss: () -> Unit, onCloseLyric: () -> Unit) {
        val context = LocalContext.current
        val settings = remember { DesktopLyricSettings.getSettings(context) }

        var fontSize by remember { mutableStateOf(settings.fontSize) }
        var textColor by remember { mutableStateOf(settings.textColor) }
        var locked by remember { mutableStateOf(settings.enabled) }

        val colorScheme = MaterialTheme.colorScheme

        val presetColors = remember {
            listOf(
                Color.WHITE, Color.parseColor("#E0E0E0"), Color.RED,
                Color.parseColor("#FF5722"), Color.parseColor("#FF9800"), Color.YELLOW,
                Color.GREEN, Color.parseColor("#4CAF50"), Color.BLUE,
                Color.parseColor("#2196F3"), Color.parseColor("#9C27B0"), Color.parseColor("#E91E63"),
                Color.parseColor("#FFD700"), Color.parseColor("#FF69B4"), Color.parseColor("#00BCD4")
            )
        }

        val allColors = remember(colorScheme.primary) {
            listOf(colorScheme.primary.toArgb()) + presetColors
        }

        fun saveSettings() {
            DesktopLyricSettings.saveSettings(
                context,
                DesktopLyricSettings(
                    fontSize = fontSize, textColor = textColor,
                    positionX = settings.positionX, positionY = settings.positionY,
                    enabled = locked
                )
            )
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.width(320.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = com.xiaowei.player.i18n.Strings.get("lyric_settings"),
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(28.dp).offset(x = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onCloseLyric() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                val colorListState = rememberLazyListState()
                LazyRow(
                    state = colorListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = colorListState),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(allColors) { index, color ->
                        val selected = if (index == 0) textColor == DesktopLyricSettings.THEME_COLOR else textColor == color
                        Surface(
                            shape = CircleShape,
                            color = androidx.compose.ui.graphics.Color(color),
                            border = if (selected) BorderStroke(2.dp, colorScheme.primary) else null,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        if (index == 0) {
                                            textColor = DesktopLyricSettings.THEME_COLOR
                                            DesktopLyricService.updateTextFollowTheme()
                                            saveSettings()
                                        } else {
                                            textColor = color
                                            DesktopLyricService.updateTextColor(color)
                                            saveSettings()
                                        }
                                    }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = com.xiaowei.player.i18n.Strings.get("lyric_font_size"),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                Slider(
                    value = fontSize,
                    onValueChange = {
                        fontSize = it
                        DesktopLyricService.updateFontSize(it)
                    },
                    onValueChangeFinished = { saveSettings() },
                    valueRange = 12f..42f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = com.xiaowei.player.i18n.Strings.get("lyric_lock_position"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = locked,
                        onCheckedChange = {
                            locked = it
                            DesktopLyricService.updateLockState(it)
                            saveSettings()
                        }
                    )
                }
            }
        }
    }

    private fun updateLyricView() {
        lyricTextView.setTextColor(currentTextColor)
        lyricTextView.textSize = currentFontSize
        if (currentLyrics.isEmpty()) {
            android.util.Log.d("KARAOKE_DBG", "updateLyricView: lyrics empty, clear")
            lyricTextView.text = ""
            displayedLyric = ""
            lyricTextView.setKaraoke(null, 0L, 0L)
            stopKaraokeTick()
            return
        }
        val currentLine = if (currentLineIndex in currentLyrics.indices) currentLyrics[currentLineIndex] else {
            android.util.Log.d("KARAOKE_DBG", "updateLyricView: idx $currentLineIndex out of range ${currentLyrics.size}")
            return
        }

        if (currentLine.words.isEmpty()) {
            android.util.Log.d("KARAOKE_DBG", "updateLyricView: plain line (no words) text='${currentLine.text.take(20)}'")
            currentNextLineTimeMs = if (currentLineIndex + 1 < currentLyrics.size) {
                currentLyrics[currentLineIndex + 1].timeMs
            } else {
                Long.MAX_VALUE
            }
            if (currentLine.text == displayedLyric) return
            displayedLyric = currentLine.text
            lyricTextView.setKaraoke(null, 0L, 0L)
            lyricTextView.text = displayedLyric
            stopKaraokeTick()
        } else {
            val nextLineTimeMs = if (currentLineIndex + 1 < currentLyrics.size) {
                currentLyrics[currentLineIndex + 1].timeMs
            } else {
                currentLine.timeMs + 4000L
            }
            currentNextLineTimeMs = nextLineTimeMs
            val livePos = computeLivePos()
            android.util.Log.d("KARAOKE_DBG", "updateLyricView: KARAOKE line idx=$currentLineIndex text='${currentLine.text.take(20)}' words=${currentLine.words.size} pos=${currentPositionMs}ms livePos=${livePos}ms nextLineMs=$nextLineTimeMs isPlaying=$isPlaying choreographerCb=${if (choreographerCallback != null) "running" else "stopped"}")
            if (currentLine.text != displayedLyric) {
                displayedLyric = currentLine.text
                lyricTextView.text = displayedLyric
            }
            lyricTextView.setKaraoke(currentLine.words, livePos, nextLineTimeMs)
            startKaraokeTick()
        }
    }

    private fun computeLivePos(): Long {
        if (!isPlaying || currentPositionUpdateNanos == 0L) return currentPositionMs
        val elapsedMs = (System.nanoTime() - currentPositionUpdateNanos) / 1_000_000L
        if (elapsedMs in 0..2000L) {
            return currentPositionMs + elapsedMs
        }
        return currentPositionMs
    }

    private var choreographerCallback: android.view.Choreographer.FrameCallback? = null
    private var frameCounter = 0
    private var currentNextLineTimeMs = Long.MAX_VALUE

    private fun startKaraokeTick() {
        if (choreographerCallback != null) {
            android.util.Log.d("KARAOKE_DBG", "startKaraokeTick: already running, skip")
            return
        }
        android.util.Log.d("KARAOKE_DBG", "startKaraokeTick: registering Choreographer callback")
        val cb = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (choreographerCallback == null) {
                    android.util.Log.d("KARAOKE_DBG", "doFrame: callback null, stop chain")
                    return
                }
                frameCounter++
                if (isPlaying) {
                    val livePos = computeLivePos()
                    if (livePos >= currentNextLineTimeMs && currentLineIndex + 1 < currentLyrics.size) {
                        val newIdx = LyricsParser.findCurrentLine(
                            currentLyrics.map { com.xiaowei.player.data.LyricLine(it.timeMs, it.text, emptyList()) },
                            livePos
                        )
                        if (newIdx > currentLineIndex && newIdx in currentLyrics.indices) {
                            android.util.Log.d("KARAOKE_DBG", "doFrame: livePos=$livePos >= nextLineMs=$currentNextLineTimeMs, advance idx $currentLineIndex -> $newIdx")
                            currentLineIndex = newIdx
                            updateLyricView()
                            android.view.Choreographer.getInstance().postFrameCallback(this)
                            return
                        }
                    }
                    lyricTextView.updateKaraokePosition(livePos)
                    if (frameCounter % 30 == 0) {
                        android.util.Log.d("KARAOKE_DBG", "doFrame: playing livePos=${livePos}ms")
                    }
                } else {
                    lyricTextView.updateKaraokePosition(currentPositionMs)
                    if (frameCounter % 30 == 0) {
                        android.util.Log.d("KARAOKE_DBG", "doFrame: paused, pos=$currentPositionMs")
                    }
                }
                android.view.Choreographer.getInstance().postFrameCallback(this)
            }
        }
        choreographerCallback = cb
        android.view.Choreographer.getInstance().postFrameCallback(cb)
    }

    private fun stopKaraokeTick() {
        choreographerCallback = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::windowManager.isInitialized || !::layoutParams.isInitialized) return
        val newSize = Point()
        windowManager.defaultDisplay.getRealSize(newSize)
        screenWidth = newSize.x
        screenHeight = newSize.y
        val halfW = screenWidth / 2
        layoutParams.x = layoutParams.x.coerceIn(-halfW, halfW)
        val viewH = rootLayout.height
        layoutParams.y = layoutParams.y.coerceIn(0, (screenHeight - viewH).coerceAtLeast(0))
        try {
            windowManager.updateViewLayout(rootLayout, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "onConfigurationChanged", e)
        }
        updateLyricMaxWidth()
        if (followTheme) applyThemeColor()
    }

    override fun onDestroy() {
        instance = null
        themeColorCallback?.let {
            com.xiaowei.player.data.ThemePrefs.get(this).removeColorChangedListener(it)
        }
        themeColorCallback = null
        stopKaraokeTick()
        hideSettings()
        settingsLifecycleOwner?.dispatchEvent(Lifecycle.Event.ON_PAUSE)
        settingsLifecycleOwner?.dispatchEvent(Lifecycle.Event.ON_STOP)
        settingsLifecycleOwner?.dispatchEvent(Lifecycle.Event.ON_DESTROY)
        settingsLifecycleOwner = null
        try { windowManager.removeView(rootLayout) } catch (e: Exception) { Log.e(TAG, "onDestroy", e) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private class KaraokeTextView(context: android.content.Context) : TextView(context) {
    private var words: List<DesktopLyricService.FloatingLyricWord> = emptyList()
    private var positionMs: Long = 0L
    private var nextLineTimeMs: Long = 0L
    private var hasKaraoke: Boolean = false
    private var pendingKaraoke: Boolean = false
    private var drawCounter = 0

    fun setKaraoke(words: List<DesktopLyricService.FloatingLyricWord>?, positionMs: Long, nextLineTimeMs: Long) {
        if (words == null) {
            android.util.Log.d("KARAOKE_DBG", "KaraokeTV.setKaraoke: null words, clear shader")
            hasKaraoke = false
            this.words = emptyList()
            paint.shader = null
            invalidate()
            return
        }
        hasKaraoke = true
        this.words = words
        this.positionMs = positionMs
        this.nextLineTimeMs = nextLineTimeMs
        android.util.Log.d("KARAOKE_DBG", "KaraokeTV.setKaraoke: words=${words.size} pos=${positionMs}ms nextLineMs=$nextLineTimeMs firstWordTime=${words.firstOrNull()?.timeMs} lastWordTime=${words.lastOrNull()?.timeMs}")
        invalidate()
    }

    fun updateKaraokePosition(positionMs: Long) {
        if (!hasKaraoke) return
        this.positionMs = positionMs
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (hasKaraoke) {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!hasKaraoke || words.isEmpty()) {
            paint.shader = null
            super.onDraw(canvas)
            return
        }
        val text = text?.toString() ?: ""
        if (text.isEmpty()) {
            paint.shader = null
            super.onDraw(canvas)
            return
        }
        val layout = layout
        if (layout == null) {
            paint.shader = null
            super.onDraw(canvas)
            return
        }
        val textWidth = layout.width.toFloat().coerceAtLeast(1f)
        val totalChars = text.length

        val sungColor = currentTextColor
        val r = Color.red(sungColor)
        val g = Color.green(sungColor)
        val b = Color.blue(sungColor)
        val dimColor = Color.argb(90, r, g, b)

        var avgSpan = 300L
        if (words.size >= 2) {
            var totalSpan = 0L
            for (i in 0 until words.size - 1) {
                totalSpan += words[i + 1].timeMs - words[i].timeMs
            }
            avgSpan = (totalSpan / (words.size - 1)).coerceIn(80L, 800L)
        }
        val lastWordEndTime = words.last().timeMs + avgSpan

        var sungCount = 0
        var dimCount = 0
        var progressCount = 0

        val workPaint = Paint(paint)
        workPaint.shader = null

        var charOffset = 0
        for (i in words.indices) {
            val w = words[i]
            val startChar = charOffset.coerceAtMost(totalChars)
            val endChar = (charOffset + w.text.length).coerceAtMost(totalChars)
            charOffset = endChar
            if (endChar <= startChar) continue
            val endTime = if (i + 1 < words.size) words[i + 1].timeMs else lastWordEndTime
            val span = (endTime - w.timeMs).coerceAtLeast(1L)
            val wordText = w.text

            when {
                positionMs >= endTime -> {
                    sungCount++
                    workPaint.color = sungColor
                    drawWordSegments(canvas, layout, wordText, startChar, endChar, workPaint)
                }
                positionMs < w.timeMs -> {
                    dimCount++
                    workPaint.color = dimColor
                    drawWordSegments(canvas, layout, wordText, startChar, endChar, workPaint)
                }
                else -> {
                    progressCount++
                    val progress = ((positionMs - w.timeMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                    drawWordSegments(canvas, layout, wordText, startChar, endChar, workPaint, wordText.length * progress, sungColor, dimColor)
                }
            }
        }
        drawCounter++
        if (drawCounter % 30 == 0) {
            android.util.Log.d("KARAOKE_DBG", "KaraokeTV.onDraw: pos=${positionMs}ms words=${words.size} sung=$sungCount dim=$dimCount progress=$progressCount textWidth=$textWidth")
        }
    }

    private fun drawWordSegments(
        canvas: Canvas,
        layout: Layout,
        wordText: String,
        startChar: Int,
        endChar: Int,
        workPaint: Paint,
        splitInWord: Float = -1f,
        sungColor: Int = 0,
        dimColor: Int = 0
    ) {
        var drawn = 0
        while (drawn < wordText.length && startChar + drawn < endChar) {
            val abs = startChar + drawn
            val line = layout.getLineForOffset(abs)
            val lineEnd = layout.getLineEnd(line).coerceAtMost(endChar)
            val count = lineEnd - abs
            if (count <= 0) break
            val segment = wordText.substring(drawn, drawn + count)
            val x = layout.getLineLeft(line) + layout.getPrimaryHorizontal(abs)
            val baseline = layout.getLineBaseline(line).toFloat()
            if (splitInWord < 0f) {
                canvas.drawText(segment, x, baseline, workPaint)
            } else {
                val segWidth = workPaint.measureText(segment)
                val segStart = drawn.toFloat()
                val segEnd = (drawn + count).toFloat()
                val lineTop = layout.getLineTop(line).toFloat()
                val lineBottom = layout.getLineBottom(line).toFloat()
                when {
                    segEnd <= splitInWord -> {
                        workPaint.color = sungColor
                        canvas.drawText(segment, x, baseline, workPaint)
                    }
                    segStart >= splitInWord -> {
                        workPaint.color = dimColor
                        canvas.drawText(segment, x, baseline, workPaint)
                    }
                    else -> {
                        val splitX = x + segWidth * ((splitInWord - segStart) / count)
                        workPaint.color = sungColor
                        canvas.save()
                        canvas.clipRect(x, lineTop, splitX, lineBottom)
                        canvas.drawText(segment, x, baseline, workPaint)
                        canvas.restore()
                        workPaint.color = dimColor
                        canvas.save()
                        canvas.clipRect(splitX, lineTop, x + segWidth, lineBottom)
                        canvas.drawText(segment, x, baseline, workPaint)
                        canvas.restore()
                    }
                }
            }
            drawn += count
        }
    }
}

data class DesktopLyricSettings(
    @Dimension(unit = Dimension.SP) val fontSize: Float = 20f,
    @ColorInt val textColor: Int = THEME_COLOR,
    val positionX: Int = 0,
    val positionY: Int = 200,
    val enabled: Boolean = false
) {
    companion object {
        const val THEME_COLOR = Int.MIN_VALUE

        fun getSettings(context: Context): DesktopLyricSettings {
            val dao = com.xiaowei.player.data.db.AppDatabase.get(context).desktopLyricSettingsDao()
            val entity = dao.getSync()
            return if (entity != null) {
                DesktopLyricSettings(
                    fontSize = entity.fontSize,
                    textColor = entity.textColor,
                    positionX = entity.positionX,
                    positionY = entity.positionY,
                    enabled = entity.enabled
                )
            } else {

                DesktopLyricSettings()
            }
        }

        fun saveSettings(context: Context, settings: DesktopLyricSettings) {
            val dao = com.xiaowei.player.data.db.AppDatabase.get(context).desktopLyricSettingsDao()
            dao.upsertSync(
                com.xiaowei.player.data.db.DesktopLyricSettingsEntity(
                    id = 0,
                    fontSize = settings.fontSize,
                    textColor = settings.textColor,
                    positionX = settings.positionX,
                    positionY = settings.positionY,
                    enabled = settings.enabled
                )
            )
        }

        fun savePosition(context: Context, x: Int, y: Int) {
            val dao = com.xiaowei.player.data.db.AppDatabase.get(context).desktopLyricSettingsDao()
            val current = dao.getSync()
            dao.upsertSync(
                com.xiaowei.player.data.db.DesktopLyricSettingsEntity(
                    id = 0,
                    fontSize = current?.fontSize ?: 20f,
                    textColor = current?.textColor ?: DesktopLyricSettings.THEME_COLOR,
                    positionX = x,
                    positionY = y,
                    enabled = current?.enabled ?: false
                )
            )
        }
    }
}
