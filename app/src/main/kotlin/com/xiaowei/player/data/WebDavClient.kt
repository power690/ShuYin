package com.xiaowei.player.data

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit

object WebDavClient {

    private const val TAG = "WebDavClient"

    const val ERROR_AUTH = "webdav_error_auth"
    const val ERROR_NETWORK = "webdav_error_network"
    const val ERROR_PATH = "webdav_error_path"
    const val ERROR_UNKNOWN = "webdav_error_unknown"
    const val ERROR_NONE = ""

    data class DavEntry(
        val href: String,
        val isDir: Boolean,
        val sizeBytes: Long,
        val lastModifiedMs: Long
    )

    class DavException(val errorKey: String, message: String) : Exception(message)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun propfind(account: WebDavAccount, targetUrl: String): List<DavEntry> {
        val body = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
<d:prop>
<d:resourcetype/>
<d:getcontentlength/>
<d:getlastmodified/>
</d:prop>
</d:propfind>""".trimIndent()
        val request = Request.Builder()
            .url(targetUrl)
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaType()))
            .header("Depth", "1")
            .header("Authorization", account.authHeader())
            .build()
        val selfPath = decodeSegment(urlPathOf(targetUrl)).trimEnd('/')
        val entries = try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> {
                        throw DavException(ERROR_AUTH, "auth failed ${response.code}")
                    }
                    response.code == 404 -> {
                        throw DavException(ERROR_PATH, "path not found")
                    }
                    !response.isSuccessful -> {
                        throw DavException(ERROR_UNKNOWN, "propfind failed ${response.code}")
                    }
                    else -> parseMultistatus(response.body?.string() ?: "")
                }
            }
        } catch (e: DavException) {
            throw e
        } catch (e: Exception) {
            throw DavException(ERROR_NETWORK, "network failed: ${e.message}")
        }
        return entries.filter {
            decodeSegment(it.href).trimEnd('/') != selfPath
        }
    }

    fun buildUrl(account: WebDavAccount, path: String): String {
        val base = account.normalizedUrl
        val suffix = if (path.isBlank() || path == "/") "" else path.trim()
        return "$base${encodePath(suffix)}"
    }

    fun originOf(account: WebDavAccount): String {
        val parsed = account.normalizedUrl.toHttpUrlOrNull() ?: return account.normalizedUrl
        return "${parsed.scheme}://${parsed.host}${if (parsed.port != 80 && parsed.port != 443) ":${parsed.port}" else ""}"
    }

    fun resolveEntryUrl(account: WebDavAccount, href: String): String {
        val trimmedHref = href.trim()
        if (trimmedHref.startsWith("http://") || trimmedHref.startsWith("https://")) {
            return trimmedHref
        }
        return originOf(account) + trimmedHref
    }

    fun urlPathOf(url: String): String {
        return url.toHttpUrlOrNull()?.encodedPath ?: "/"
    }

    fun decodeSegment(segment: String): String {
        return try {
            java.net.URLDecoder.decode(segment, "UTF-8")
        } catch (_: Exception) {
            segment
        }
    }

    private fun encodePath(path: String): String {
        val segments = path.split("/").filter { it.isNotEmpty() }
        val encoded = segments.joinToString("/") { seg ->
            seg.split(" ").joinToString("%20") { java.net.URLEncoder.encode(it, "UTF-8") }
        }
        return if (encoded.isEmpty()) "" else "/$encoded"
    }

    private fun parseMultistatus(xml: String): List<DavEntry> {
        val entries = mutableListOf<DavEntry>()
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))
            var currentHref: String? = null
            var isDir = false
            var size = 0L
            var mtime = 0L
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.substringAfter(':').lowercase()
                        if (name == "response") {
                            isDir = false
                            size = 0L
                            mtime = 0L
                        }
                        if (name == "collection") isDir = true
                        if (name == "getcontentlength") {
                            size = parser.nextText().toLongOrNull() ?: 0L
                        }
                        if (name == "getlastmodified") {
                            val raw = parser.nextText()
                            mtime = parseHttpDate(raw)
                        }
                        if (name == "href" && currentHref == null) {
                            currentHref = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.substringAfter(':').lowercase()
                        if (name == "response" && currentHref != null) {
                            entries.add(DavEntry(currentHref, isDir, size, mtime))
                            currentHref = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "parse multistatus failed: ${e.message}")
        }
        return entries
    }

    private fun parseHttpDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ"
        )
        for (pattern in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("GMT")
                return sdf.parse(raw)?.time ?: 0L
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    fun requestHeaders(account: WebDavAccount): Map<String, String> {
        return mapOf("Authorization" to account.authHeader())
    }
}
