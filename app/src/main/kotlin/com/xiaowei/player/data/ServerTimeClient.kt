package com.xiaowei.player.data

import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object ServerTimeClient {

    private val timeEndpoints = listOf(
        "https://www.apple.com/library/test/success.html",
        "https://www.cloudflare.com/cdn-cgi/trace",
        "https://gitee.com",
        "https://www.baidu.com"
    )

    fun fetchEpochMillis(totalTimeoutMillis: Long): Long? {
        val deadline = System.currentTimeMillis() + totalTimeoutMillis
        for (endpoint in timeEndpoints) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) return null
            val timeout = remaining.coerceAtMost(5000L)
            val time = requestServerTime(endpoint, timeout)
            if (time != null) return time
        }
        return null
    }

    private fun requestServerTime(endpoint: String, timeoutMillis: Long): Long? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMillis.toInt()
                readTimeout = timeoutMillis.toInt()
                useCaches = false
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            connection.responseCode
            parseDateHeader(connection.getHeaderField("Date"))
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseDateHeader(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.isLenient = false
            format.parse(value)?.time
        } catch (_: Exception) {
            null
        }
    }
}
