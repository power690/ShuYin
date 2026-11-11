package com.xiaowei.player.data

import android.util.Log
import com.xiaowei.player.i18n.Strings
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val UPDATE_URL_CN = "https://gitee.com/cuteqwwtr/ShuYin/raw/main/update_cn.json"
    private const val UPDATE_URL_INTL = "https://raw.githubusercontent.com/power690/ShuYin/main/update.json"

    const val EXPECTED_CRC32 = "D7987BBE"
    const val EXPECTED_MD5 = "A8E7A71D7B777F4C8F0DB3412C9BB88B"
    const val EXPECTED_SHA1 = "6D7229BD1382AD1E358364471C8B2D8514EA75E0"
    const val EXPECTED_SHA256 = "304592EA89B4E81A07C66CE7F2E90C3DE9EEC25141222F41F6E0353F8AE0FDD1"

    private fun updateUrl(): String {
        val lang = Strings.currentLanguageCode()
        return if (lang.startsWith("zh")) UPDATE_URL_CN else UPDATE_URL_INTL
    }

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val updateLog: String,
        val forceUpdate: Boolean
    )

    fun check(currentVersionCode: Int): UpdateInfo? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url(updateUrl())
                .header("Cache-Control", "no-cache")
                .build()
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
                val forceUpdate = json.optBoolean("forceUpdate", false)
                UpdateInfo(newCode, newName, downloadUrl, log, forceUpdate)
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
