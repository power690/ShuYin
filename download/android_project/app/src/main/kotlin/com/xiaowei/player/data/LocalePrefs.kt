package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

class LocalePrefs private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val languageCodeState = mutableStateOf<String?>(prefs.getString(KEY_LANGUAGE, null))

    var languageCode: String?
        get() = languageCodeState.value
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value).apply()
            languageCodeState.value = value

            com.xiaowei.player.i18n.Strings.setCurrentLanguage(value)

            updateLocaleDefault(value)
        }

    private fun updateLocaleDefault(value: String?) {
        val locale = if (value != null) {
            val parts = value.split("-")
            if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
        } else {
            getSystemLocale()
        }
        Locale.setDefault(locale)
    }

    private fun getSystemLocale(): Locale {
        val systemConfig = android.content.res.Resources.getSystem().configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            systemConfig.locales[0]
        } else {
            @Suppress("DEPRECATION")
            systemConfig.locale ?: Locale.getDefault()
        }
    }

    companion object {
        private const val PREFS_NAME = "locale_prefs"
        private const val KEY_LANGUAGE = "language_code"

        @Volatile
        private var instance: LocalePrefs? = null

        fun get(context: Context): LocalePrefs {
            return instance ?: synchronized(this) {
                instance ?: LocalePrefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
