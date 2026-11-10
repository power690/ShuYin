package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

class LibraryGridPrefs private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val gridColumnsState = mutableStateOf(
        VALID_COLUMNS.firstOrNull { it == prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_COLUMNS) }
            ?: DEFAULT_COLUMNS
    )

    var gridColumns: Int
        get() = gridColumnsState.value
        set(value) {
            val clamped = VALID_COLUMNS.firstOrNull { it == value } ?: DEFAULT_COLUMNS
            gridColumnsState.value = clamped
            prefs.edit().putInt(KEY_GRID_COLUMNS, clamped).apply()
        }

    companion object {
        private const val PREFS_NAME = "library_grid_prefs"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val DEFAULT_COLUMNS = 2
        private val VALID_COLUMNS = listOf(2, 3, 4)

        @Volatile
        private var instance: LibraryGridPrefs? = null

        fun get(context: Context): LibraryGridPrefs {
            return instance ?: synchronized(this) {
                instance ?: LibraryGridPrefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
