package com.xiaowei.player.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.mpatric.mp3agic.Mp3File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository(private val context: Context) {

    suspend fun loadAllMusic(): List<Song> = withContext(Dispatchers.IO) {
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

        val coverJobs = mutableListOf<Job>()

        val coverSemaphore = Semaphore(12)

        val loadedPaths = HashSet<String>()
        val mmr = MediaMetadataRetriever()
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

                        val artUri = if (albumId > 0) {
                            ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            )
                        } else null

                        val lyrics = readLyrics(data, mime, mmr)

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
                                albumArtUri = artUri,
                                lyrics = lyrics,
                                mimeType = mime,
                                size = cursor.getLong(sizeCol)
                            )
                        )

                        if (data.isNotBlank() && loadedPaths.add(data)) {
                            val job = launch {
                                coverSemaphore.withPermit {
                                    try {
                                        EmbeddedCoverFetcher.loadCoverBytes(data)
                                    } catch (_: Exception) {

                                    }
                                }
                            }
                            coverJobs.add(job)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query MediaStore", e)
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }

        coverJobs.forEach { it.join() }

        Log.i(TAG, "Loaded ${songs.size} songs from MediaStore (covers preloaded)")
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
        val coverJobs = mutableListOf<Job>()
        val coverSemaphore = Semaphore(12)
        val loadedPaths = HashSet<String>()
        val mmr = MediaMetadataRetriever()

        try {

            val audioFiles = mutableListOf<File>()
            collectAudioFiles(rootFile, supportedExtensions, audioFiles)

            audioFiles.sortByDescending { it.lastModified() }

            coroutineScope {
                audioFiles.forEach { file ->
                    val filePath = file.absolutePath
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

                        val artUri = if (albumId > 0) {
                            ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            )
                        } else null

                        val lyrics = readLyrics(filePath, mimeTypeStr, MediaMetadataRetriever())

                        songs.add(
                            Song(
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
                                albumArtUri = artUri,
                                lyrics = lyrics,
                                mimeType = mimeTypeStr,
                                size = file.length(),
                                source = "custom_path"  
                            )
                        )

                        if (loadedPaths.add(filePath)) {
                            val job = launch {
                                coverSemaphore.withPermit {
                                    try {
                                        EmbeddedCoverFetcher.loadCoverBytes(filePath)
                                    } catch (_: Exception) {}
                                }
                            }
                            coverJobs.add(job)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to read metadata: $filePath - ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan custom path: $rootPath", e)
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }

        coverJobs.forEach { it.join() }
        Log.i(TAG, "Loaded ${songs.size} songs from custom path: $rootPath")
        songs
    }

    private fun collectAudioFiles(
        dir: File,
        extensions: Set<String>,
        result: MutableList<File>
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                collectAudioFiles(child, extensions, result)
            } else if (child.isFile) {
                val ext = child.extension.lowercase()
                if (ext in extensions) {
                    result.add(child)
                }
            }
        }
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

    private fun readLyrics(filePath: String, mime: String?, mmr: MediaMetadataRetriever): String? {
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

    /**
     * 切歌时按需重读歌词。
     *
     * 背景：Android 11+ 分区存储生效后，未授予 MANAGE_EXTERNAL_STORAGE 时，
     * 用 File API 读不到拆分歌词文件（.lrc）；用户在主界面授权后，需要一次
     * 重读机会把歌词补回来。
     *
     * 逻辑与 loadAllMusic 内的 readLyrics 完全一致，确保读到的歌词与扫描库时一致。
     */
    suspend fun reloadLyrics(song: Song): String? = withContext(Dispatchers.IO) {
        if (song.data.isBlank()) return@withContext null
        val mmr = MediaMetadataRetriever()
        try {
            readLyrics(song.data, song.mimeType, mmr)
        } catch (e: Exception) {
            Log.w(TAG, "reloadLyrics failed: ${song.data} - ${e.message}")
            null
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    private fun readLrcFile(songPath: String): String? {
        val songFile = File(songPath)
        val dir = songFile.parentFile ?: return null
        val base = songFile.nameWithoutExtension
        val candidates = listOf(
            File(dir, "$base.lrc"),
            File(dir, "${base}.LRC"),
            File(dir, "$base - 歌词.lrc"),
            File(dir, "$base.lrc.txt"),
            File(dir, "$base.txt")
        )
        for (f in candidates) {
            if (f.exists() && f.canRead()) {
                return f.readText(Charsets.UTF_8)
            }
        }
        return null
    }

    private fun readMp3Uslt(filePath: String): String? {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return try {
            val mp3 = Mp3File(filePath)
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
                albumArtUri = firstSong.albumArtUri,
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
                    albumArtUri = first.albumArtUri,
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
                    coverUri = picked.first().albumArtUri,
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
                    coverUri = longTracks.first().albumArtUri,
                    songs = longTracks
                )
            )
        }

        return cards
    }

    companion object {
        private const val TAG = "MusicRepository"
    }
}
