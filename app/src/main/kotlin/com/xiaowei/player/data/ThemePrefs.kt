package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.CopyOnWriteArrayList

class ThemePrefs private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val themeColorIndexState = mutableStateOf(prefs.getInt(KEY_THEME_COLOR_INDEX, 0))
    val dynamicColorEnabledState = mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))

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

    companion object {
        const val PREFS_NAME = "theme_prefs"
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
