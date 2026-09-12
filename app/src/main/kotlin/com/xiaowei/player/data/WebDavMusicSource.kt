package com.xiaowei.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class WebDavMusicSource(private val context: Context) {

    data class ScanResult(
        val songs: List<Song>,
        val errorKey: String
    )

    private class DavFileEntry(
        val url: String,
        val name: String,
        val ext: String,
        val sizeBytes: Long,
        val lastModifiedMs: Long,
        val lrcUrl: String?
    )

    private class CacheEntry(
        val url: String,
        val sizeBytes: Long,
        val lastModifiedMs: Long,
        val song: Song
    )

    suspend fun loadSongs(account: WebDavAccount, forceRescan: Boolean = false): ScanResult = withContext(Dispatchers.IO) {
        if (!forceRescan) {
            val cached = loadCache(account)
            if (cached.isNotEmpty()) {
                val cachedSongs = cached.values.map { it.song }
                return@withContext ScanResult(cachedSongs, WebDavClient.ERROR_NONE)
            }
        }
        val autoMode = account.path.trim().trim('/').isEmpty()
        val rootUrl = WebDavClient.buildUrl(account, account.normalizedPath)
        val rootEntries = try {
            WebDavClient.propfind(account, rootUrl)
        } catch (e: WebDavClient.DavException) {
            return@withContext ScanResult(emptyList(), e.errorKey)
        }

        val supported = setOf("mp3", "flac", "ogg", "m4a", "aac", "wav", "opus", "ape")
        val collected = mutableListOf<DavFileEntry>()
        val pendingDirs = ArrayDeque<String>()
        val visitedDirs = HashSet<String>()
        for (entry in rootEntries.filter { it.isDir }) {
            if (!isHiddenEntry(entry.href)) {
                pendingDirs.add(WebDavClient.resolveEntryUrl(account, entry.href))
            }
        }
        collectFiles(account, rootEntries, supported, collected)

        var guard = 0
        val maxRequests = if (autoMode) 384 else 512
        while (pendingDirs.isNotEmpty() && guard < maxRequests) {
            guard++
            val dir = pendingDirs.removeFirst()
            if (!visitedDirs.add(dir)) continue
            val subEntries = try {
                WebDavClient.propfind(account, dir)
            } catch (e: WebDavClient.DavException) {
                continue
            }
            collectFiles(account, subEntries, supported, collected)
            val subDirs = subEntries.count { it.isDir && !isHiddenEntry(it.href) }
            for (entry in subEntries.filter { it.isDir }) {
                if (!isHiddenEntry(entry.href)) {
                    pendingDirs.add(WebDavClient.resolveEntryUrl(account, entry.href))
                }
            }
        }

        if (collected.isEmpty()) {
            return@withContext ScanResult(emptyList(), WebDavClient.ERROR_NONE)
        }

        val cache = loadCache(account)
        val hitCount = AtomicInteger(0)
        val semaphore = Semaphore(SCAN_PARALLELISM)
        val songs = try {
            coroutineScope {
                collected.map { davFile ->
                    async {
                        semaphore.withPermit {
                            resolveSong(account, davFile, cache, hitCount)
                        }
                    }
                }.awaitAll()
            }.filterNotNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "scan failed", e)
            return@withContext ScanResult(emptyList(), WebDavClient.ERROR_NETWORK)
        }

        if (hitCount.get() != songs.size || cache.size != songs.size) {
            saveCache(account, songs, collected)
        }

        Log.i(TAG, "WebDav loaded ${songs.size} songs from ${account.name}")
        ScanResult(songs, WebDavClient.ERROR_NONE)
    }

    suspend fun fetchLyricsFor(account: WebDavAccount, song: Song): String? = withContext(Dispatchers.IO) {
        if (song.source != "webdav") return@withContext null
        try {
            val name = WebDavClient.decodeSegment(song.data.substringAfterLast('/'))
            if (name.isBlank()) return@withContext null
            val base = name.substringBeforeLast('.')
            val ext = name.substringAfterLast('.', "").lowercase()
            val parentUrl = song.data.substringBeforeLast('/')
            val dirEntries = WebDavClient.propfind(account, parentUrl)
            val nameMap = HashMap<String, String>()
            for (entry in dirEntries) {
                if (entry.isDir) continue
                val n = WebDavClient.decodeSegment(entry.href.trimEnd('/').substringAfterLast('/'))
                if (n.isNotBlank()) {
                    nameMap[n] = WebDavClient.resolveEntryUrl(account, entry.href)
                }
            }
            val candidates = listOf(
                "$base.lrc",
                "$base.LRC",
                "$base - 歌词.lrc",
                "$base.lrc.txt",
                "$base.ttml",
                "$base.TTML",
                "$base - 歌词.ttml",
                "$base.txt"
            )
            var lrcUrl: String? = null
            for (candidate in candidates) {
                val url = nameMap[candidate]
                if (url != null) {
                    lrcUrl = url
                    break
                }
            }
            val davFile = DavFileEntry(
                url = song.data,
                name = name,
                ext = ext,
                sizeBytes = song.size,
                lastModifiedMs = song.dateAdded * 1000L,
                lrcUrl = lrcUrl
            )
            readLyricsForFile(account, davFile)
        } catch (_: Exception) {
            null
        }
    }

    private fun collectFiles(
        account: WebDavAccount,
        entries: List<WebDavClient.DavEntry>,
        supported: Set<String>,
        collected: MutableList<DavFileEntry>
    ) {
        val nameMap = HashMap<String, WebDavClient.DavEntry>()
        for (entry in entries) {
            val n = WebDavClient.decodeSegment(entry.href.trimEnd('/').substringAfterLast('/'))
            if (n.isNotBlank()) nameMap[n] = entry
        }
        for (entry in entries) {
            if (entry.isDir) continue
            val decodedName = WebDavClient.decodeSegment(entry.href.trimEnd('/').substringAfterLast('/'))
            if (decodedName.isBlank() || decodedName.startsWith(".")) continue
            val ext = decodedName.substringAfterLast('.', "").lowercase()
            if (ext in supported) {
                val base = decodedName.substringBeforeLast('.')
                var lrcUrl: String? = null
                val candidates = listOf(
                    "$base.lrc",
                    "$base.LRC",
                    "$base - 歌词.lrc",
                    "$base.lrc.txt",
                    "$base.ttml",
                    "$base.TTML",
                    "$base - 歌词.ttml",
                    "$base.txt"
                )
                for (candidate in candidates) {
                    val target = nameMap[candidate]
                    if (target != null && !target.isDir) {
                        lrcUrl = WebDavClient.resolveEntryUrl(account, target.href)
                        break
                    }
                }
                collected.add(
                    DavFileEntry(
                        url = WebDavClient.resolveEntryUrl(account, entry.href),
                        name = decodedName,
                        ext = ext,
                        sizeBytes = entry.sizeBytes,
                        lastModifiedMs = entry.lastModifiedMs,
                        lrcUrl = lrcUrl
                    )
                )
            }
        }
    }

    private fun isHiddenEntry(href: String): Boolean {
        val name = WebDavClient.decodeSegment(href.trimEnd('/').substringAfterLast('/'))
        return name.isBlank() || name.startsWith(".")
    }

    private fun resolveSong(
        account: WebDavAccount,
        davFile: DavFileEntry,
        cache: Map<String, CacheEntry>,
        hitCount: AtomicInteger
    ): Song? {
        val cached = cache[davFile.url]
        if (cached != null &&
            cached.sizeBytes == davFile.sizeBytes &&
            cached.lastModifiedMs == davFile.lastModifiedMs
        ) {
            hitCount.incrementAndGet()
            return cached.song
        }
        return readSongFromNetwork(account, davFile)
    }

    private fun readSongFromNetwork(
        account: WebDavAccount,
        davFile: DavFileEntry
    ): Song? {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var duration = 0L
        var year = 0
        var track = 0
        var mimeType: String? = null
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(davFile.url, WebDavClient.requestHeaders(account))
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
            albumArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?.takeIf { it.isNotBlank() }
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            year = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull() ?: 0
            track = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.split("/")?.firstOrNull()?.toIntOrNull() ?: 0
            mimeType = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        } catch (e: Exception) {
            Log.w(TAG, "webdav metadata failed: ${davFile.name} - ${e.message}")
        } finally {
            try { mmr.release() } catch (_: Throwable) {}
        }

        val lyrics = readLyricsForFile(account, davFile)

        val fallbackName = davFile.name.substringBeforeLast('.')
        val nameParts = fallbackName.split(" - ", " -", "- ", "-").map { it.trim() }.filter { it.isNotEmpty() }
        val finalTitle = title ?: fallbackName
        val finalArtist = artist ?: nameParts.getOrNull(1) ?: ""
        val finalAlbum = album ?: ""

        val id = davFile.url.hashCode().toLong() and 0xFFFFFFFFL
        val albumId = "$finalArtist|$finalAlbum".hashCode().toLong() and 0xFFFFFFFFL
        val artistId = finalArtist.hashCode().toLong() and 0xFFFFFFFFL

        return Song(
            id = id,
            title = finalTitle,
            artist = finalArtist,
            artistId = artistId,
            album = finalAlbum,
            albumId = albumId,
            albumArtist = albumArtist,
            duration = duration,
            data = davFile.url,
            dateAdded = davFile.lastModifiedMs / 1000,
            track = track,
            year = year,
            lyrics = lyrics?.takeIf { it.isNotBlank() },
            mimeType = mimeType,
            size = davFile.sizeBytes,
            source = "webdav"
        )
    }

    private fun downloadText(url: String, account: WebDavAccount): String? {
        return try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Authorization", account.authHeader())
                .build()
            WebDavClientHttpClient.shared.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadRange(url: String, account: WebDavAccount, maxBytes: Int): ByteArray? {
        return try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Authorization", account.authHeader())
                .header("Range", "bytes=0-${maxBytes - 1}")
                .build()
            WebDavClientHttpClient.shared.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val stream = response.body?.byteStream() ?: return null
                val buffer = ByteArray(maxBytes)
                var read = 0
                while (read < maxBytes) {
                    val n = stream.read(buffer, read, maxBytes - read)
                    if (n < 0) break
                    read += n
                }
                if (read <= 0) null else buffer.copyOf(read)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readLyricsForFile(account: WebDavAccount, davFile: DavFileEntry): String? {
        if (davFile.lrcUrl != null) {
            val text = downloadText(davFile.lrcUrl, account)
            if (!text.isNullOrBlank()) return text
        }
        return try {
            when (davFile.ext) {
                "flac" -> readFlacLyrics(davFile.url, account)
                "mp3" -> readMp3Lyrics(davFile.url, account)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readFlacLyrics(url: String, account: WebDavAccount): String? {
        val first = downloadRange(url, account, FLAC_HEAD_FIRST) ?: return null
        val direct = FlacLyricsParser.readLyricsFromHead(flacBodyOf(first))
        if (direct != null) return direct
        val second = downloadRange(url, account, FLAC_HEAD_MAX) ?: return null
        return FlacLyricsParser.readLyricsFromHead(flacBodyOf(second))
    }

    private fun flacBodyOf(bytes: ByteArray): ByteArray {
        if (bytes.size >= 4 &&
            bytes[0] == 0x66.toByte() &&
            bytes[1] == 0x4C.toByte() &&
            bytes[2] == 0x61.toByte() &&
            bytes[3] == 0x43.toByte()
        ) return bytes
        if (bytes.size >= 10 &&
            bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() &&
            bytes[2] == '3'.code.toByte()
        ) {
            val tagSize = ((bytes[6].toLong() and 0x7F) shl 21) or
                ((bytes[7].toLong() and 0x7F) shl 14) or
                ((bytes[8].toLong() and 0x7F) shl 7) or
                (bytes[9].toLong() and 0x7F)
            val start = (10 + tagSize).toInt()
            if (start in 0..(bytes.size - 4)) {
                return bytes.copyOfRange(start, bytes.size)
            }
        }
        return bytes
    }

    private fun readMp3Lyrics(url: String, account: WebDavAccount): String? {
        val head = downloadRange(url, account, MP3_HEAD_FIRST) ?: return null
        val direct = parseMp3Uslt(head)
        if (direct != null) return direct
        val tagSize = id3v2TagSize(head) ?: return null
        val needed = tagSize + 10
        if (needed <= head.size || needed > MP3_HEAD_MAX) return null
        val bigger = downloadRange(url, account, needed) ?: return null
        return parseMp3Uslt(bigger)
    }

    private fun id3v2TagSize(bytes: ByteArray): Int? {
        if (bytes.size < 10) return null
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) return null
        return ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)
    }

    private fun parseMp3Uslt(bytes: ByteArray): String? {
        val tagSize = id3v2TagSize(bytes) ?: return null
        if (tagSize <= 0) return null
        val versionMajor = bytes[3].toInt() and 0xFF
        val flags = bytes[5].toInt() and 0xFF
        val limit = minOf(bytes.size, 10 + tagSize)
        var pos = 10
        if ((flags and 0x40) != 0 && bytes.size >= 14) {
            val extSize = if (versionMajor == 4) {
                ((bytes[10].toInt() and 0x7F) shl 21) or
                    ((bytes[11].toInt() and 0x7F) shl 14) or
                    ((bytes[12].toInt() and 0x7F) shl 7) or
                    (bytes[13].toInt() and 0x7F)
            } else {
                ((bytes[10].toInt() and 0xFF) shl 24) or
                    ((bytes[11].toInt() and 0xFF) shl 16) or
                    ((bytes[12].toInt() and 0xFF) shl 8) or
                    (bytes[13].toInt() and 0xFF)
            }
            pos += if (versionMajor == 4) extSize else 4 + extSize
        }
        while (pos < limit) {
            if (versionMajor == 2) {
                if (pos + 6 > limit) break
                val id = String(bytes, pos, 3, Charsets.ISO_8859_1)
                val frameSize = ((bytes[pos + 3].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 4].toInt() and 0xFF) shl 8) or
                    (bytes[pos + 5].toInt() and 0xFF)
                if (frameSize <= 0) break
                if (id == "ULT") {
                    if (pos + 6 + frameSize > bytes.size) return null
                    return decodeUslt(bytes.copyOfRange(pos + 6, pos + 6 + frameSize))
                }
                pos += 6 + frameSize
            } else {
                if (pos + 10 > limit) break
                val id = String(bytes, pos, 4, Charsets.ISO_8859_1)
                val frameSize = if (versionMajor == 4) {
                    ((bytes[pos + 4].toInt() and 0x7F) shl 21) or
                        ((bytes[pos + 5].toInt() and 0x7F) shl 14) or
                        ((bytes[pos + 6].toInt() and 0x7F) shl 7) or
                        (bytes[pos + 7].toInt() and 0x7F)
                } else {
                    ((bytes[pos + 4].toInt() and 0xFF) shl 24) or
                        ((bytes[pos + 5].toInt() and 0xFF) shl 16) or
                        ((bytes[pos + 6].toInt() and 0xFF) shl 8) or
                        (bytes[pos + 7].toInt() and 0xFF)
                }
                if (frameSize <= 0) break
                if (id == "USLT") {
                    if (pos + 10 + frameSize > bytes.size) return null
                    return decodeUslt(bytes.copyOfRange(pos + 10, pos + 10 + frameSize))
                }
                pos += 10 + frameSize
            }
        }
        return null
    }

    private fun decodeUslt(data: ByteArray): String? {
        if (data.size < 5) return null
        val enc = data[0].toInt() and 0xFF
        var start = -1
        if (enc == 1 || enc == 2) {
            var i = 4
            while (i + 1 < data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) {
                    start = i + 2
                    break
                }
                i++
            }
        } else {
            var i = 4
            while (i < data.size) {
                if (data[i].toInt() == 0) {
                    start = i + 1
                    break
                }
                i++
            }
        }
        if (start < 0 || start >= data.size) return null
        val text = when (enc) {
            0 -> String(data, start, data.size - start, Charsets.ISO_8859_1)
            1 -> String(data, start, data.size - start, Charsets.UTF_16)
            2 -> String(data, start, data.size - start, Charsets.UTF_16BE)
            else -> String(data, start, data.size - start, Charsets.UTF_8)
        }
        return text.takeIf { it.isNotBlank() }
    }

    private fun cacheFile(account: WebDavAccount): File {
        return File(context.filesDir, CACHE_PREFIX + account.id.take(8))
    }

    private fun loadCache(account: WebDavAccount): Map<String, CacheEntry> {
        try {
            val file = cacheFile(account)
            if (!file.exists() || file.length() == 0L) return emptyMap()
            DataInputStream(BufferedInputStream(FileInputStream(file), 65536)).use { dis ->
                if (dis.readInt() != CACHE_MAGIC || dis.readInt() != CACHE_VERSION) return emptyMap()
                val count = dis.readInt()
                if (count < 0 || count > 100_000) return emptyMap()
                val map = LinkedHashMap<String, CacheEntry>(count * 2)
                repeat(count) {
                    val url = readString(dis) ?: return emptyMap()
                    val sizeBytes = dis.readLong()
                    val lastModifiedMs = dis.readLong()
                    val song = readSong(dis)
                    map[url] = CacheEntry(url, sizeBytes, lastModifiedMs, song)
                }
                return map
            }
        } catch (_: Exception) {
            return emptyMap()
        }
    }

    private fun saveCache(account: WebDavAccount, songs: List<Song>, files: List<DavFileEntry>) {
        try {
            val stats = files.associate { it.url to it }
            val target = cacheFile(account)
            val tmp = File(target.parentFile, target.name + ".tmp")
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 65536)).use { dos ->
                dos.writeInt(CACHE_MAGIC)
                dos.writeInt(CACHE_VERSION)
                dos.writeInt(songs.size)
                for (song in songs) {
                    val stat = stats[song.data]
                    writeString(dos, song.data)
                    dos.writeLong(stat?.sizeBytes ?: 0L)
                    dos.writeLong(stat?.lastModifiedMs ?: 0L)
                    writeSong(dos, song)
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun writeSong(dos: DataOutputStream, song: Song) {
        dos.writeLong(song.id)
        writeString(dos, song.title)
        writeString(dos, song.artist)
        dos.writeLong(song.artistId)
        writeString(dos, song.album)
        dos.writeLong(song.albumId)
        writeString(dos, song.albumArtist)
        dos.writeLong(song.duration)
        writeString(dos, song.data)
        dos.writeLong(song.dateAdded)
        dos.writeInt(song.track)
        dos.writeInt(song.year)
        writeString(dos, song.genre)
        writeCompressed(dos, song.lyrics)
        writeString(dos, song.mimeType)
        dos.writeLong(song.size)
        writeString(dos, song.source)
    }

    private fun readSong(dis: DataInputStream): Song {
        val id = dis.readLong()
        val title = readString(dis) ?: ""
        val artist = readString(dis) ?: ""
        val artistId = dis.readLong()
        val album = readString(dis) ?: ""
        val albumId = dis.readLong()
        val albumArtist = readString(dis)
        val duration = dis.readLong()
        val data = readString(dis) ?: ""
        val dateAdded = dis.readLong()
        val track = dis.readInt()
        val year = dis.readInt()
        val genre = readString(dis)
        val lyrics = readCompressed(dis)
        val mimeType = readString(dis)
        val size = dis.readLong()
        val source = readString(dis) ?: "webdav"
        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = artistId,
            album = album,
            albumId = albumId,
            albumArtist = albumArtist,
            duration = duration,
            data = data,
            dateAdded = dateAdded,
            track = track,
            year = year,
            genre = genre,
            lyrics = lyrics,
            mimeType = mimeType,
            size = size,
            source = source
        )
    }

    private fun writeString(dos: DataOutputStream, value: String?) {
        if (value == null) {
            dos.writeInt(-1)
            return
        }
        val bytes = value.toByteArray(Charsets.UTF_8)
        dos.writeInt(bytes.size)
        dos.write(bytes)
    }

    private fun readString(dis: DataInputStream): String? {
        val len = dis.readInt()
        if (len < 0) return null
        val bytes = ByteArray(len)
        dis.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun writeCompressed(dos: DataOutputStream, value: String?) {
        if (value == null) {
            dos.writeInt(-1)
            return
        }
        val bytes = value.toByteArray(Charsets.UTF_8)
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip -> gzip.write(bytes) }
        val compressed = bos.toByteArray()
        dos.writeInt(compressed.size)
        dos.write(compressed)
    }

    private fun readCompressed(dis: DataInputStream): String? {
        val len = dis.readInt()
        if (len < 0) return null
        val compressed = ByteArray(len)
        dis.readFully(compressed)
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
            gzip.readBytes().toString(Charsets.UTF_8)
        }
    }

    companion object {
        private const val TAG = "WebDavMusicSource"
        private const val SCAN_PARALLELISM = 8
        private const val CACHE_MAGIC = 0x57444156
        private const val CACHE_VERSION = 3
        private const val CACHE_PREFIX = ".webdav_scan_cache_"
        private const val FLAC_HEAD_FIRST = 4096
        private const val FLAC_HEAD_MAX = 2097152
        private const val MP3_HEAD_FIRST = 65536
        private const val MP3_HEAD_MAX = 2097152
    }
}

object WebDavClientHttpClient {
    val shared: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient()
    }
}
