package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

class ArtistCoverPrefs private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val selectionVersionState = mutableStateOf(0)

    fun getSelectedCover(artistName: String): String? =
        prefs.getString(artistName, null)

    fun setSelectedCover(artistName: String, coverPath: String) {
        prefs.edit().putString(artistName, coverPath).apply()
        selectionVersionState.value = selectionVersionState.value + 1
    }

    fun clearArtist(artistName: String) {
        if (prefs.contains(artistName)) {
            prefs.edit().remove(artistName).apply()
            selectionVersionState.value = selectionVersionState.value + 1
        }
    }

    companion object {
        private const val PREFS_NAME = "artist_cover_prefs"

        @Volatile
        private var instance: ArtistCoverPrefs? = null

        fun get(context: Context): ArtistCoverPrefs {
            return instance ?: synchronized(this) {
                instance ?: ArtistCoverPrefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
