package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

class AudioMixPrefs private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val mixWithOthersState = mutableStateOf(prefs.getBoolean(KEY_MIX_WITH_OTHERS, false))

    var mixWithOthers: Boolean
        get() = mixWithOthersState.value
        set(value) {
            prefs.edit().putBoolean(KEY_MIX_WITH_OTHERS, value).apply()
            mixWithOthersState.value = value
        }

    companion object {
        private const val PREFS_NAME = "audio_mix_prefs"
        private const val KEY_MIX_WITH_OTHERS = "mix_with_others"

        @Volatile
        private var instance: AudioMixPrefs? = null

        fun get(context: Context): AudioMixPrefs {
            return instance ?: synchronized(this) {
                instance ?: AudioMixPrefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
