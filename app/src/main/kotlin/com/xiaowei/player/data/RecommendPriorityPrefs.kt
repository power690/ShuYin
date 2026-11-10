package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

class RecommendPriorityPrefs private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val priorityState = mutableStateOf(prefs.getString(KEY_PRIORITY, PRIORITY_LOCAL) ?: PRIORITY_LOCAL)

    var priority: String
        get() = priorityState.value
        set(value) {
            prefs.edit().putString(KEY_PRIORITY, value).apply()
            priorityState.value = value
        }

    companion object {
        const val PRIORITY_LOCAL = "local"
        const val PRIORITY_NETWORK = "network"
        const val PRIORITY_MIXED = "mixed"

        private const val PREFS_NAME = "recommend_priority_prefs"
        private const val KEY_PRIORITY = "priority"

        @Volatile
        private var instance: RecommendPriorityPrefs? = null

        fun get(context: Context): RecommendPriorityPrefs =
            instance ?: synchronized(this) {
                instance ?: RecommendPriorityPrefs(context.applicationContext).also { instance = it }
            }
    }
}
