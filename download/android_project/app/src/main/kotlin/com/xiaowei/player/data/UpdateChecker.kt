package com.xiaowei.player.data

import android.util.Log
import com.xiaowei.player.i18n.Strings
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val UPDATE_URL = "https://raw.githubusercontent.com/power690/ShuYin/main/update.json"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val updateLog: String
    )

    fun check(currentVersionCode: Int): UpdateInfo? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(UPDATE_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val newCode = json.optInt("versionCode", 0)
                if (newCode <= currentVersionCode) return null
                val newName = json.optString("versionName", "")
                val downloadUrl = json.optString("downloadUrl", "")
                if (newName.isBlank() || downloadUrl.isBlank()) return null
                val log = pickLog(json)
                UpdateInfo(newCode, newName, downloadUrl, log)
            }
        } catch (e: Exception) {
            Log.w(TAG, "check update failed: ${e.message}")
            null
        }
    }

    private fun pickLog(json: JSONObject): String {
        val logs = json.optJSONObject("updateLog") ?: return ""
        val lang = Strings.currentLanguageCode()
        if (logs.has(lang)) {
            val v = logs.optString(lang, "")
            if (v.isNotBlank()) return v
        }
        val default = json.optString("defaultLanguage", "en")
        if (logs.has(default)) {
            val v = logs.optString(default, "")
            if (v.isNotBlank()) return v
        }
        if (logs.has("en")) return logs.optString("en", "")
        return ""
    }
}
