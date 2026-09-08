package com.xiaowei.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.mpatric.mp3agic.Mp3File
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
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class MusicRepository(private val context: Context) {

    private class FileStat(val size: Long, val mtime: Long)

    private class ScanTree(
        val files: List<File>,
        val fileStats: Map<String, FileStat>,
        val dirSigs: Map<String, Long>
    )

    private class ScanCacheEntry(
        val path: String,
        val sizeBytes: Long,
        val lastModifiedMs: Long,
        val dirSig: Long,
        val song: Song
    )

    suspend fun loadAllMusic(): List<Song> = withContext(Dispatchers.IO) {
        lrcDirCache.clear()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val songs = mutableListOf<Song>()

        try {
            context.contentResolver.query(
                collection, projection, selection, null, sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val albumArtistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                coroutineScope {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val albumId = cursor.getLong(albumIdCol)

                        val rawData = cursor.getString(dataCol) ?: ""
                        val data = resolveRealFilePath(rawData)
                        val title = cursor.getString(titleCol) ?: com.xiaowei.player.i18n.Strings.get("unknown_title")
                        val mime = cursor.getString(mimeCol)
                        val displayName = cursor.getString(displayCol) ?: title

                        val lyrics = readLyrics(data, mime)

                        songs.add(
                            Song(
                                id = id,
                                title = title,
                                artist = cursor.getString(artistCol) ?: "",
                                artistId = cursor.getLong(artistIdCol),
                                album = cursor.getString(albumCol) ?: "",
                                albumId = albumId,
                                albumArtist = cursor.getString(albumArtistCol),
                                duration = cursor.getLong(durationCol),
                                data = data,
                                dateAdded = cursor.getLong(dateCol),
                                track = cursor.getInt(trackCol),
                                year = cursor.getInt(yearCol),
                                lyrics = lyrics,
                                mimeType = mime,
                                size = cursor.getLong(sizeCol)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query MediaStore", e)
        }

        Log.i(TAG, "Loaded ${songs.size} songs from MediaStore")
        songs
    }

    suspend fun loadMusicFromPath(rootPath: String): List<Song> = withContext(Dispatchers.IO) {
        val rootFile = File(rootPath)
        if (!rootFile.exists() || !rootFile.isDirectory) {
            Log.w(TAG, "Custom path does not exist or not a directory: $rootPath")
            return@withContext emptyList()
        }

        val supportedExtensions = setOf("mp3", "flac", "ogg", "m4a", "aac", "wav", "opus")
        val songs = mutableListOf<Song>()

        lrcDirCache.clear()

        try {

            val cache = loadScanCache()
            val scanTree = collectAudioFiles(rootFile, supportedExtensions)

            val audioFiles = scanTree.files.sortedByDescending { file ->
                scanTree.fileStats[file.absolutePath]?.mtime ?: 0L
            }

            val hitCount = AtomicInteger(0)
            val semaphore = Semaphore(SCAN_PARALLELISM)
            coroutineScope {
                val loaded = audioFiles.map { file ->
                    async {
                        semaphore.withPermit {
                            resolveSong(file, scanTree, cache, hitCount)
                        }
                    }
                }.awaitAll()
                songs.addAll(loaded.filterNotNull())
            }

            if (hitCount.get() != songs.size || cache.size != songs.size) {
                saveScanCache(songs, scanTree)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan custom path: $rootPath", e)
        }

        Log.i(TAG, "Loaded ${songs.size} songs from custom path: $rootPath")
        songs
    }

    private fun readSongFromFile(file: File): Song? {
        val filePath = file.absolutePath
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(filePath)
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.nameWithoutExtension
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val albumArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            val yearStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val year = yearStr?.toIntOrNull() ?: 0
            val mimeTypeStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val trackStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val track = trackStr?.split("/")?.firstOrNull()?.toIntOrNull() ?: 0

            val id = filePath.hashCode().toLong() and 0xFFFFFFFFL
            val albumId = "$artist|$album".hashCode().toLong() and 0xFFFFFFFFL
            val artistId = artist.hashCode().toLong() and 0xFFFFFFFFL

            val lyrics = readLyrics(filePath, mimeTypeStr)

            return Song(
                id = id,
                title = title,
                artist = artist,
                artistId = artistId,
                album = album,
                albumId = albumId,
                albumArtist = albumArtist,
                duration = duration,
                data = filePath,
                dateAdded = file.lastModified() / 1000,
                track = track,
                year = year,
                lyrics = lyrics,
                mimeType = mimeTypeStr,
                size = file.length(),
                source = "custom_path"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read metadata: $filePath - ${e.message}")
            return null
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    private fun resolveSong(
        file: File,
        tree: ScanTree,
        cache: Map<String, ScanCacheEntry>,
        hitCount: AtomicInteger
    ): Song? {
        val path = file.absolutePath
        val cached = cache[path]
        if (cached != null) {
            val stat = tree.fileStats[path]
            val sig = tree.dirSigs[file.parentFile?.absolutePath]
            if (stat != null &&
                cached.sizeBytes == stat.size &&
                cached.lastModifiedMs == stat.mtime &&
                cached.dirSig == sig
            ) {
                hitCount.incrementAndGet()
                return cached.song
            }
        }
        return readSongFromFile(file)
    }

    private suspend fun collectAudioFiles(
        dir: File,
        extensions: Set<String>
    ): ScanTree {
        val children = dir.listFiles()
            ?: return ScanTree(emptyList(), emptyMap(), emptyMap())
        val files = mutableListOf<File>()
        val stats = HashMap<String, FileStat>()
        val subDirs = mutableListOf<File>()
        val nonAudio = mutableListOf<Pair<String, Long>>()
        for (child in children) {
            if (child.isDirectory) {
                subDirs.add(child)
            } else if (child.isFile) {
                val ext = child.extension.lowercase()
                if (ext in extensions) {
                    files.add(child)
                    stats[child.absolutePath] = FileStat(child.length(), child.lastModified())
                } else {
                    nonAudio.add(child.name to child.lastModified())
                }
            }
        }
        val dirSigs = HashMap<String, Long>()
        dirSigs[dir.absolutePath] = dirSignature(nonAudio)
        if (subDirs.isEmpty()) {
            return ScanTree(files, stats, dirSigs)
        }
        val fromSubDirs = coroutineScope {
            subDirs.map { sub ->
                async { collectAudioFiles(sub, extensions) }
            }.awaitAll()
        }
        val mergedFiles = files.toMutableList()
        val mergedStats = HashMap(stats)
        for (r in fromSubDirs) {
            mergedFiles.addAll(r.files)
            mergedStats.putAll(r.fileStats)
            dirSigs.putAll(r.dirSigs)
        }
        return ScanTree(mergedFiles, mergedStats, dirSigs)
    }

    private fun dirSignature(entries: List<Pair<String, Long>>): Long {
        if (entries.isEmpty()) return 0L
        val sb = StringBuilder()
        for ((name, mtime) in entries.sortedBy { it.first }) {
            sb.append(name).append(':').append(mtime).append('|')
        }
        val digest = MessageDigest.getInstance("MD5")
            .digest(sb.toString().toByteArray(Charsets.UTF_8))
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (digest[i].toLong() and 0xFFL)
        }
        return v
    }

    private fun resolveRealFilePath(rawPath: String): String {
        if (rawPath.isBlank()) return rawPath

        if (File(rawPath).exists()) return rawPath

        if (rawPath.contains("%")) {
            try {
                val decoded = java.net.URLDecoder.decode(rawPath, "UTF-8")
                if (decoded != rawPath && File(decoded).exists()) {
                    return decoded
                }
            } catch (_: Exception) {

            }
        }

        return rawPath
    }

    private fun readLyrics(filePath: String, mime: String?): String? {
        if (filePath.isBlank()) return null
        val lrc = readLrcFile(filePath)
        if (!lrc.isNullOrBlank()) return lrc

        return try {
            val lower = filePath.lowercase()
            when {
                mime?.contains("flac", ignoreCase = true) == true || lower.endsWith(".flac") ->
                    FlacLyricsParser.readLyrics(filePath)
                mime?.contains("mp3", ignoreCase = true) == true || lower.endsWith(".mp3") ->
                    readMp3Uslt(filePath)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    
    suspend fun reloadLyrics(song: Song): String? = withContext(Dispatchers.IO) {
        if (song.data.isBlank()) return@withContext null
        try {
            readLyrics(song.data, song.mimeType)
        } catch (e: Exception) {
            Log.w(TAG, "reloadLyrics failed: ${song.data} - ${e.message}")
            null
        }
    }

    private val lrcDirCache = ConcurrentHashMap<String, Set<String>>()

    private fun readLrcFile(songPath: String): String? {
        val songFile = File(songPath)
        val dir = songFile.parentFile ?: return null
        val base = songFile.nameWithoutExtension
        val dirNames = lrcDirCache.getOrPut(dir.absolutePath) {
            dir.listFiles()?.mapTo(HashSet()) { it.name } ?: emptySet()
        }
        val candidates = listOf(
            "$base.lrc",
            "$base.LRC",
            "$base - 歌词.lrc",
            "$base.lrc.txt",
            "$base.txt"
        )
        for (name in candidates) {
            if (name in dirNames) {
                val f = File(dir, name)
                if (f.canRead()) {
                    return f.readText(Charsets.UTF_8)
                }
            }
        }
        return null
    }

    private fun readMp3Uslt(filePath: String): String? {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return try {
            val mp3 = Mp3File(filePath, false)
            if (mp3.hasId3v2Tag()) {
                val tag = mp3.id3v2Tag
                val lyrics = tag.lyrics
                if (!lyrics.isNullOrBlank()) lyrics else null
            } else null
        } catch (e: Exception) {
            null
        } catch (e: NoClassDefFoundError) {

            null
        }
    }

    private fun scanCacheFile(): File = File(context.filesDir, CACHE_FILE_NAME)

    private fun loadScanCache(): Map<String, ScanCacheEntry> {
        try {
            val file = scanCacheFile()
            if (!file.exists() || file.length() == 0L) return emptyMap()
            DataInputStream(BufferedInputStream(FileInputStream(file), 65536)).use { dis ->
                if (dis.readInt() != CACHE_MAGIC || dis.readInt() != CACHE_VERSION) return emptyMap()
                val count = dis.readInt()
                if (count < 0 || count > 1_000_000) return emptyMap()
                val map = HashMap<String, ScanCacheEntry>(count * 2)
                repeat(count) {
                    val path = readString(dis) ?: return emptyMap()
                    val sizeBytes = dis.readLong()
                    val lastModifiedMs = dis.readLong()
                    val dirSig = dis.readLong()
                    val song = readSong(dis)
                    map[path] = ScanCacheEntry(path, sizeBytes, lastModifiedMs, dirSig, song)
                }
                return map
            }
        } catch (e: Exception) {
            Log.w(TAG, "Scan cache invalid, ignored: ${e.message}")
            return emptyMap()
        }
    }

    private fun saveScanCache(songs: List<Song>, tree: ScanTree) {
        try {
            val target = scanCacheFile()
            val tmp = File(target.parentFile, CACHE_FILE_NAME + ".tmp")
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 65536)).use { dos ->
                dos.writeInt(CACHE_MAGIC)
                dos.writeInt(CACHE_VERSION)
                dos.writeInt(songs.size)
                for (song in songs) {
                    val path = song.data
                    val stat = tree.fileStats[path]
                    writeString(dos, path)
                    dos.writeLong(stat?.size ?: 0L)
                    dos.writeLong(stat?.mtime ?: 0L)
                    dos.writeLong(tree.dirSigs[File(path).parentFile?.absolutePath] ?: 0L)
                    writeSong(dos, song)
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                Log.w(TAG, "Failed to replace scan cache file")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save scan cache: ${e.message}")
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
        val source = readString(dis) ?: "mediastore"
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

    private val artistSplitRegex: Regex by lazy {
        Regex("""\s*(?:[&,、|/;；]|feat\.?|ft\.?|vs\.?|和|与|并)\s*""", RegexOption.IGNORE_CASE)
    }

    private fun splitArtists(rawArtist: String): List<String> {
        if (rawArtist.isBlank()) return emptyList()
        val parts = artistSplitRegex.split(rawArtist)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val seen = LinkedHashSet<String>()
        for (p in parts) seen.add(p)
        return seen.toList()
    }

    fun buildArtists(songs: List<Song>): Pair<List<Artist>, Map<String, List<Song>>> {

        val appearCount = HashMap<String, Int>()
        val unknownArtistKey = com.xiaowei.player.i18n.Strings.get("unknown_artist")
        for (s in songs) {
            val names = splitArtists(s.artist)
            if (names.isEmpty()) {
                appearCount[unknownArtistKey] = (appearCount[unknownArtistKey] ?: 0) + 1
            } else {
                for (n in names) appearCount[n] = (appearCount[n] ?: 0) + 1
            }
        }

        val grouped = LinkedHashMap<String, MutableList<Song>>()
        for (s in songs) {
            val names = splitArtists(s.artist)
            val mainArtist = when {
                names.isEmpty() -> unknownArtistKey
                names.size == 1 -> names.first()
                else -> {
                    val matched = names.firstOrNull { (appearCount[it] ?: 0) >= 2 }
                    matched ?: s.displayArtist
                }
            }
            grouped.getOrPut(mainArtist) { mutableListOf() }.add(s)
        }

        val artists = grouped.entries.map { (name, list) ->
            val firstSong = list.first()
            Artist(
                id = if (list.size == 1 && splitArtists(name).size > 1) name.hashCode().toLong()
                     else firstSong.artistId,
                name = name,
                songCount = list.size,
                albumCount = list.distinctBy { it.albumId }.size,
                totalDuration = list.sumOf { it.duration },

                firstSongData = firstSong.data
            )
        }.sortedBy { it.displayName }

        val songMap = LinkedHashMap<String, List<Song>>()
        for (a in artists) {
            songMap[a.displayName] = grouped[a.name]?.toList() ?: emptyList()
        }
        return artists to songMap
    }

    fun buildAlbums(songs: List<Song>): List<Album> {
        return songs.groupBy { it.albumId }
            .map { (albumId, list) ->
                val first = list.first()
                Album(
                    id = albumId,
                    name = first.album,
                    artist = first.albumArtist ?: first.displayArtist,
                    year = list.mapNotNull { if (it.year > 0) it.year else null }.maxOrNull() ?: 0,
                    songCount = list.size,
                    totalDuration = list.sumOf { it.duration },
                    firstSongData = first.data
                )
            }
            .sortedBy { it.displayName }
    }

    fun buildRecommendCards(
        songs: List<Song>,
        artists: List<Artist>,
        albums: List<Album>,
        artistSongMap: Map<String, List<Song>> = emptyMap()
    ): List<RecommendCard> {
        val cards = mutableListOf<RecommendCard>()

        val eligible = songs.filter { it.duration > 60_000 }
        if (eligible.isNotEmpty()) {
            val picked = eligible.shuffled().take(20)
            cards.add(
                RecommendCard(
                    title = com.xiaowei.player.i18n.Strings.get("recommend_random_title"),
                    subtitle = com.xiaowei.player.i18n.Strings.get("recommend_random_subtitle", picked.size),
                    songs = picked
                )
            )
        }

        val longTracks = songs.filter { it.duration > 5 * 60_000 }
            .sortedByDescending { it.duration }
            .take(30)
            .shuffled()
            .take(20)
        if (longTracks.isNotEmpty()) {
            cards.add(
                RecommendCard(
                    title = com.xiaowei.player.i18n.Strings.get("recommend_long_title"),
                    subtitle = com.xiaowei.player.i18n.Strings.get("recommend_long_subtitle", longTracks.size),
                    songs = longTracks
                )
            )
        }

        return cards
    }

    companion object {
        private const val TAG = "MusicRepository"
        private const val SCAN_PARALLELISM = 64
        private const val CACHE_MAGIC = 0x4D555343
        private const val CACHE_VERSION = 1
        private const val CACHE_FILE_NAME = ".music_scan_cache"
    }
}
