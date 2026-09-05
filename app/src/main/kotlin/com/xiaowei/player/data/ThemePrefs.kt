package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.CopyOnWriteArrayList

class ThemePrefs private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val themeColorIndexState = mutableStateOf(prefs.getInt(KEY_THEME_COLOR_INDEX, DEFAULT_THEME_COLOR_INDEX))
    val dynamicColorEnabledState = mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val coverColorEnabledState = mutableStateOf(prefs.getBoolean(KEY_COVER_COLOR, false))
    val coverColorState = mutableStateOf<Int?>(null)
    val materialStyleState = mutableStateOf(prefs.getString(KEY_MATERIAL_STYLE, DEFAULT_MATERIAL_STYLE) ?: DEFAULT_MATERIAL_STYLE)
    val playerStyleState = mutableStateOf(prefs.getString(KEY_PLAYER_STYLE, DEFAULT_PLAYER_STYLE) ?: DEFAULT_PLAYER_STYLE)
    val immersiveLyricsState = mutableStateOf(prefs.getBoolean(KEY_IMMERSIVE_LYRICS, false))
    val lastPrimaryColorState = mutableStateOf(prefs.getInt(KEY_LAST_PRIMARY_COLOR, DEFAULT_PRIMARY_COLOR))

    private val colorChangedListeners = CopyOnWriteArrayList<() -> Unit>()

    fun addColorChangedListener(listener: () -> Unit) {
        colorChangedListeners.add(listener)
    }

    fun removeColorChangedListener(listener: () -> Unit) {
        colorChangedListeners.remove(listener)
    }

    private fun notifyColorChanged() {
        for (listener in colorChangedListeners) {
            listener()
        }
    }

    var themeColorIndex: Int
        get() = themeColorIndexState.value
        set(value) {
            themeColorIndexState.value = value
            prefs.edit().putInt(KEY_THEME_COLOR_INDEX, value).apply()
            notifyColorChanged()
        }

    var dynamicColorEnabled: Boolean
        get() = dynamicColorEnabledState.value
        set(value) {
            dynamicColorEnabledState.value = value
            prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
            notifyColorChanged()
        }

    var coverColorEnabled: Boolean
        get() = coverColorEnabledState.value
        set(value) {
            coverColorEnabledState.value = value
            prefs.edit().putBoolean(KEY_COVER_COLOR, value).apply()
            notifyColorChanged()
        }

    var coverColor: Int?
        get() = coverColorState.value
        set(value) {
            coverColorState.value = value
            notifyColorChanged()
        }

    var materialStyle: String
        get() = materialStyleState.value
        set(value) {
            materialStyleState.value = value
            prefs.edit().putString(KEY_MATERIAL_STYLE, value).apply()
            notifyColorChanged()
        }

    var playerStyle: String
        get() = playerStyleState.value
        set(value) {
            playerStyleState.value = value
            prefs.edit().putString(KEY_PLAYER_STYLE, value).apply()
        }

    var immersiveLyrics: Boolean
        get() = immersiveLyricsState.value
        set(value) {
            immersiveLyricsState.value = value
            prefs.edit().putBoolean(KEY_IMMERSIVE_LYRICS, value).apply()
        }

    var lastPrimaryColor: Int
        get() = lastPrimaryColorState.value
        set(value) {
            if (lastPrimaryColorState.value == value) return
            lastPrimaryColorState.value = value
            prefs.edit().putInt(KEY_LAST_PRIMARY_COLOR, value).apply()
            notifyColorChanged()
        }

    companion object {
        const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME_COLOR_INDEX = "theme_color_index"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"
        private const val KEY_COVER_COLOR = "cover_color_enabled"
        private const val KEY_MATERIAL_STYLE = "material_style"
        private const val KEY_PLAYER_STYLE = "player_style"
        private const val KEY_IMMERSIVE_LYRICS = "immersive_lyrics"
        private const val KEY_LAST_PRIMARY_COLOR = "last_primary_color"
        const val MATERIAL_STYLE_LIQUID = "liquid"
        const val MATERIAL_STYLE_FROSTED = "frosted"
        const val DEFAULT_MATERIAL_STYLE = MATERIAL_STYLE_LIQUID
        const val PLAYER_STYLE_MD3 = "md3"
        const val PLAYER_STYLE_CLASSIC = "classic"
        const val DEFAULT_PLAYER_STYLE = PLAYER_STYLE_MD3
        const val DEFAULT_THEME_COLOR_INDEX = 1
        const val DEFAULT_PRIMARY_COLOR: Int = 0xFF005AC8.toInt()

        @Volatile
        private var instance: ThemePrefs? = null

        fun get(context: Context): ThemePrefs {
            return instance ?: synchronized(this) {
                instance ?: ThemePrefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
