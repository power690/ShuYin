package com.xiaowei.player.api

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater


object KugouApi {

    
    private val KRC_KEY = byteArrayOf(
        64, 71, 97, 119, 94, 50, 116, 71,
        81, 54, 49, 45, 206.toByte(), 210.toByte(), 110, 105
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    
    data class SongInfo(
        val hash: String,
        val songName: String,
        val singerName: String,
        val albumName: String,
        val albumId: String,
        val albumAudioId: Long,
        val durationSec: Long
    )

    
    data class LyricCandidate(
        val id: String,
        val accessKey: String,
        val song: String,
        val singer: String,
        val durationMs: Long,
        val score: Int
    )

    
    fun searchSong(keyword: String, page: Int = 1, pageSize: Int = 20): List<SongInfo> {
        return try {
            val q = java.net.URLEncoder.encode(keyword, "utf-8")
            val url = "http://mobilecdn.kugou.com/api/v3/search/song?format=json" +
                    "&keyword=$q&page=$page&pagesize=$pageSize"
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.kugou.com/")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val raw = resp.body?.string() ?: return@use emptyList()
                parseSearchResult(raw)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    
    private fun parseSearchResult(raw: String): List<SongInfo> {
        val songs = mutableListOf<SongInfo>()
        try {
            val json = JSONObject(raw)
            if (json.optInt("status") != 1) return emptyList()
            val info = json.optJSONObject("data")?.optJSONArray("info") ?: return emptyList()
            for (i in 0 until info.length()) {
                val item = info.optJSONObject(i) ?: continue
                val hash = item.optString("hash")
                if (hash.isBlank()) continue
                songs.add(SongInfo(
                    hash = hash,
                    songName = item.optString("songname").trim(),
                    singerName = item.optString("singername").trim(),
                    albumName = item.optString("album_name").trim(),
                    albumId = item.optString("album_id"),
                    albumAudioId = item.optLong("album_audio_id"),
                    durationSec = item.optLong("duration")
                ))
            }
        } catch (_: Exception) {}
        return songs
    }

    
    fun searchLyric(
        keyword: String,
        durationMs: Long,
        hash: String = "",
        albumAudioId: Long = 0
    ): List<LyricCandidate> {
        return try {
            val q = java.net.URLEncoder.encode(keyword, "utf-8")
            val url = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc" +
                    "&keyword=$q&duration=$durationMs&hash=$hash&album_audio_id=$albumAudioId&lrctxt=1"
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val raw = resp.body?.string() ?: return@use emptyList()
                parseLyricSearchResult(raw)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    
    private fun parseLyricSearchResult(raw: String): List<LyricCandidate> {
        val cands = mutableListOf<LyricCandidate>()
        try {
            val json = JSONObject(raw)
            if (json.optInt("status") != 200) return emptyList()
            val arr = json.optJSONArray("candidates") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                cands.add(LyricCandidate(
                    id = item.optString("id"),
                    accessKey = item.optString("accesskey"),
                    song = item.optString("song").trim(),
                    singer = item.optString("singer").trim(),
                    durationMs = item.optLong("duration"),
                    score = item.optInt("score")
                ))
            }
        } catch (_: Exception) {}
        return cands
    }

    
    fun downloadLyric(id: String, accessKey: String, fmt: String = "krc"): String? {
        return try {
            val url = "https://lyrics.kugou.com/download?ver=1&client=pc" +
                    "&id=$id&accesskey=$accessKey&fmt=$fmt&charset=utf8"
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string() ?: return@use null
                val json = JSONObject(raw)
                if (json.optInt("status") != 200) return@use null
                json.optString("content").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    
    fun decodeKrc(contentBase64: String): String? {
        return try {
            val raw = Base64.decode(contentBase64, Base64.DEFAULT)
            if (raw.size < 4 || raw[0] != 'k'.code.toByte() || raw[1] != 'r'.code.toByte() ||
                raw[2] != 'c'.code.toByte() || raw[3] != '1'.code.toByte()) {
                return null
            }
            val encrypted = raw.copyOfRange(4, raw.size)
            val decrypted = ByteArray(encrypted.size)
            for (i in encrypted.indices) {
                decrypted[i] = (encrypted[i].toInt() xor KRC_KEY[i % KRC_KEY.size].toInt()).toByte()
            }
            val text = zlibDecompress(decrypted)?.toString(Charsets.UTF_8) ?: return null
            if (text.startsWith("\ufeff")) text.substring(1) else text
        } catch (e: Exception) {
            null
        }
    }

    
    private fun zlibDecompress(data: ByteArray): ByteArray? {
        return try {
            val inflater = Inflater()
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 8)
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                out.write(buf, 0, n)
            }
            inflater.end()
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }
}
