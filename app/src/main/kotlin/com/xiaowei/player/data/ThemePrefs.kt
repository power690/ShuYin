package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

class ThemePrefs private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val themeColorIndexState = mutableStateOf(prefs.getInt(KEY_THEME_COLOR_INDEX, 0))
    val dynamicColorEnabledState = mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))

    var themeColorIndex: Int
        get() = themeColorIndexState.value
        set(value) {
            prefs.edit().putInt(KEY_THEME_COLOR_INDEX, value).apply()
            themeColorIndexState.value = value
        }

    var dynamicColorEnabled: Boolean
        get() = dynamicColorEnabledState.value
        set(value) {
            prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
            dynamicColorEnabledState.value = value
        }

    companion object {
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME_COLOR_INDEX = "theme_color_index"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"

        @Volatile
        private var instance: ThemePrefs? = null

        fun get(context: Context): ThemePrefs {
            return instance ?: synchronized(this) {
                instance ?: ThemePrefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
