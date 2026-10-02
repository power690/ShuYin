package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

class SetupWizardPrefs private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val wizardCompletedState = mutableStateOf(prefs.getBoolean(KEY_COMPLETED, false))

    var wizardCompleted: Boolean
        get() = wizardCompletedState.value
        set(value) {
            prefs.edit().putBoolean(KEY_COMPLETED, value).apply()
            wizardCompletedState.value = value
        }

    companion object {
        private const val PREFS_NAME = "setup_wizard_prefs"
        private const val KEY_COMPLETED = "wizard_completed"

        @Volatile
        private var instance: SetupWizardPrefs? = null

        fun get(context: Context): SetupWizardPrefs {
            return instance ?: synchronized(this) {
                instance ?: SetupWizardPrefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
