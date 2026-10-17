package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

class ClassicTutorialPrefs private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val tutorialDoneState = mutableStateOf(prefs.getBoolean(KEY_DONE, false))

    var tutorialDone: Boolean
        get() = tutorialDoneState.value
        set(value) {
            prefs.edit().putBoolean(KEY_DONE, value).apply()
            tutorialDoneState.value = value
        }

    companion object {
        private const val PREFS_NAME = "classic_tutorial_prefs"
        private const val KEY_DONE = "classic_tutorial_done"

        @Volatile
        private var instance: ClassicTutorialPrefs? = null

        fun get(context: Context): ClassicTutorialPrefs {
            return instance ?: synchronized(this) {
                instance ?: ClassicTutorialPrefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
