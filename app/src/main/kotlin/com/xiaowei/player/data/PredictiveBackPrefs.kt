package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

class PredictiveBackPrefs private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val enabledState = mutableStateOf(prefs.getBoolean(KEY_ENABLED, false))

    var enabled: Boolean
        get() = enabledState.value
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            enabledState.value = value
        }

    companion object {
        private const val PREFS_NAME = "predictive_back_prefs"
        private const val KEY_ENABLED = "enabled"

        @Volatile
        private var instance: PredictiveBackPrefs? = null

        fun get(context: Context): PredictiveBackPrefs =
            instance ?: synchronized(this) {
                instance ?: PredictiveBackPrefs(context.applicationContext).also { instance = it }
            }
    }
}
