package com.xiaowei.player.player

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
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
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.xiaowei.player.ui.theme.rememberZMusicColorScheme

class DesktopLyricService : Service() {

    data class FloatingLyricLine(val timeMs: Long, val text: String)

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
        private var isPlaying = false

        private var instance: DesktopLyricService? = null

        private var pendingUpdate = false

        fun updateLyric(lyrics: List<FloatingLyricLine>, lineIndex: Int, playing: Boolean = true) {
            currentLyrics.clear()
            currentLyrics.addAll(lyrics)
            currentLineIndex = lineIndex
            isPlaying = playing
            pendingUpdate = true

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
            }
        }

        fun updateTextColor(@ColorInt color: Int) {
            instance?.let {
                it.currentTextColor = color
                it.lyricTextView.setTextColor(color)
            }
        }

        fun updateLockState(locked: Boolean) {
            instance?.let { it.isLocked = locked }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var rootLayout: FrameLayout
    private lateinit var lyricLayout: LinearLayout
    private lateinit var lyricTextView: TextView

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var screenWidth = 0
    private var screenHeight = 0

    private var displayedLyric = ""

    private var isLocked = false
    private var currentFontSize = 20f
    @ColorInt private var currentTextColor = Color.WHITE

    private var lastTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 300

    private var settingsComposeView: ComposeView? = null
    private var settingsLifecycleOwner: ServiceLifecycleOwner? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        loadSettings()
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
        currentTextColor = settings.textColor
        isLocked = settings.enabled
    }

    @SuppressLint("InflateParams")
    private fun createLyricWindow() {
        try {
            val windowSize = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(windowSize)
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
                width = (resources.displayMetrics.widthPixels * 0.96).toInt()
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                val halfW = width / 2
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
                setPadding(dpToPx(32), dpToPx(16), dpToPx(32), dpToPx(16))
                lyricTextView = TextView(context).apply {
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
        } catch (e: SecurityException) {
            Log.e(TAG, "createLyricWindow: SecurityException", e)
        } catch (e: Exception) {
            Log.e(TAG, "createLyricWindow: Error", e)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            if (settingsComposeView != null) {
                hideSettings()
                return true
            }
            return false
        }

        if (settingsComposeView != null) {
            val popupLocation = IntArray(2)
            settingsComposeView?.getLocationOnScreen(popupLocation)
            val popupW = settingsComposeView?.width ?: 0
            val popupH = settingsComposeView?.height ?: 0
            val inPopup = event.rawX >= popupLocation[0] && event.rawX <= popupLocation[0] + popupW &&
                    event.rawY >= popupLocation[1] && event.rawY <= popupLocation[1] + popupH
            if (inPopup) return false
        }

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
                        val halfW = layoutParams.width / 2
                        val viewH = rootLayout.height
                        layoutParams.x = newX.coerceIn(-halfW, halfW)
                        layoutParams.y = newY.coerceIn(0, (screenHeight - viewH).coerceAtLeast(0))
                        windowManager.updateViewLayout(rootLayout, layoutParams)
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
                if (settingsComposeView != null) {
                    hideSettings()
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
            layoutParams.flags = layoutParams.flags or
                    (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            windowManager.updateViewLayout(rootLayout, layoutParams)

            val composeView = ComposeView(this)
            composeView.setContent {
                val isDark = android.content.res.Configuration.UI_MODE_NIGHT_YES ==
                    resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK

                val themePrefs = com.xiaowei.player.data.ThemePrefs.get(this@DesktopLyricService)
                val colorScheme = rememberZMusicColorScheme(
                    darkTheme = isDark,
                    dynamicColorEnabled = themePrefs.dynamicColorEnabled,
                    themeColorIndex = themePrefs.themeColorIndex
                )
                MaterialTheme(colorScheme = colorScheme) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures { _ -> hideSettings() }
                            }
                    ) {
                        LyricSettingsPopupContent(onDismiss = { hideSettings() })
                    }
                }
            }

            val composeParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(8) }
            composeView.layoutParams = composeParams
            lyricLayout.addView(composeView)
            settingsComposeView = composeView
        } catch (e: Exception) {
            Log.e(TAG, "showSettings: Error", e)
        }
    }

    private fun hideSettings() {
        settingsComposeView?.let {
            try { lyricLayout.removeView(it) } catch (e: Exception) { Log.e(TAG, "hideSettings", e) }
        }
        settingsComposeView = null
        try {
            layoutParams.flags = layoutParams.flags and
                    (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH).inv()
            windowManager.updateViewLayout(rootLayout, layoutParams)
        } catch (e: Exception) { Log.e(TAG, "hideSettings: flags", e) }
    }

    @Composable
    private fun LyricSettingsPopupContent(onDismiss: () -> Unit) {
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
            presetColors + listOf(colorScheme.primary.toArgb())
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
                Text(
                    text = com.xiaowei.player.i18n.Strings.get("lyric_settings"),
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(allColors, key = { _, color -> color }) { _, color ->
                        val selected = textColor == color
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
                                        textColor = color
                                        DesktopLyricService.updateTextColor(color)
                                        saveSettings()
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
            lyricTextView.text = ""
            return
        }
        val currentLine = if (currentLineIndex in currentLyrics.indices) currentLyrics[currentLineIndex] else return
        if (currentLine.text == displayedLyric) return
        displayedLyric = currentLine.text
        lyricTextView.text = displayedLyric
    }

    override fun onDestroy() {
        instance = null
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

data class DesktopLyricSettings(
    @Dimension(unit = Dimension.SP) val fontSize: Float = 20f,
    @ColorInt val textColor: Int = Color.WHITE,
    val positionX: Int = 0,
    val positionY: Int = 200,
    val enabled: Boolean = false
) {
    companion object {

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
                    textColor = current?.textColor ?: Color.WHITE,
                    positionX = x,
                    positionY = y,
                    enabled = current?.enabled ?: false
                )
            )
        }
    }
}
