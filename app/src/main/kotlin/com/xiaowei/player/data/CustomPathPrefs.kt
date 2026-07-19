package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

class CustomPathPrefs private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val pathState = mutableStateOf(prefs.getString(KEY_PATH, "") ?: "")

    var path: String
        get() = pathState.value
        set(value) {
            prefs.edit().putString(KEY_PATH, value).apply()
            pathState.value = value
        }

    companion object {
        private const val PREFS_NAME = "custom_path_prefs"
        private const val KEY_PATH = "custom_path"

        @Volatile
        private var instance: CustomPathPrefs? = null

        fun get(context: Context): CustomPathPrefs {
            return instance ?: synchronized(this) {
                instance ?: CustomPathPrefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
