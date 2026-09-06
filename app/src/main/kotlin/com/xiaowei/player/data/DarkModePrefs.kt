package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

class DarkModePrefs private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val darkModeState = mutableStateOf(prefs.getString(KEY_DARK_MODE, MODE_SYSTEM) ?: MODE_SYSTEM)

    var darkMode: String
        get() = darkModeState.value
        set(value) {
            prefs.edit().putString(KEY_DARK_MODE, value).apply()
            darkModeState.value = value
        }

    fun isDarkTheme(systemDark: Boolean): Boolean = when (darkModeState.value) {
        MODE_DARK -> true
        MODE_LIGHT -> false
        else -> systemDark
    }

    companion object {
        const val MODE_SYSTEM = "system"
        const val MODE_DARK = "dark"
        const val MODE_LIGHT = "light"

        private const val PREFS_NAME = "dark_mode_prefs"
        private const val KEY_DARK_MODE = "dark_mode"

        @Volatile
        private var instance: DarkModePrefs? = null

        fun get(context: Context): DarkModePrefs {
            return instance ?: synchronized(this) {
                instance ?: DarkModePrefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
