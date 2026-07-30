package com.xiaowei.player.lyric

import com.xiaowei.player.api.KugouApi
import com.xiaowei.player.scanner.MusicScanner
import java.io.File


internal object BatchProcessor {

    enum class FileStatus {
        NO_LYRIC,            
        ALREADY_WORD_BY_WORD,
        FIXED_LYRIC,         
        SEARCHED,            
        NO_MATCH,            
        FETCHED,             
        FETCH_FAILED,        
        MISMATCH,            
        WRITTEN,             
        WRITE_FAILED,        
        UNSUPPORTED_FORMAT   
    }

    data class TaskItem(
        val musicFile: MusicScanner.MusicFile,
        var status: FileStatus,
        var detail: String = "",
        var songHash: String? = null,  
        var lrc: String? = null        
    ) {
        val displayName: String get() = musicFile.name
    }

    
    fun scanAndDetect(dir: File): List<TaskItem> {
        val files = MusicScanner.scan(dir)
        if (files.isEmpty()) return emptyList()

        val items = mutableListOf<TaskItem>()
        files.forEach { mf ->
            val existing = try {
                LyricWriter.readLyrics(mf.file)
            } catch (_: Exception) { null }
            val source = try {
                LyricWriter.readLyricSource(mf.file)
            } catch (_: Exception) { null }
            val type = LyricTypeDetector.detect(existing, source)
            val (status, detail) = when (type) {
                LyricTypeDetector.LyricType.ALREADY_OK -> FileStatus.ALREADY_WORD_BY_WORD to "已是逐字歌词，跳过"
                LyricTypeDetector.LyricType.NEED_REPLACE -> FileStatus.FIXED_LYRIC to "检测到其他来源歌词，将替换"
                LyricTypeDetector.LyricType.NONE -> FileStatus.NO_LYRIC to "无歌词"
            }
            items.add(TaskItem(mf, status, detail))
        }
        return items
    }

    
    fun fetchLyric(item: TaskItem): Boolean {
        if (item.status == FileStatus.ALREADY_WORD_BY_WORD) return true
        if (item.status == FileStatus.UNSUPPORTED_FORMAT) return false

        val (title, artist) = MusicScanner.guessTitleArtist(item.musicFile)
        val musicDurSec = item.musicFile.durationMs / 1000
        val fileAlbum = item.musicFile.metaAlbum

        
        val existingHash = try {
            LyricWriter.readLyricRid(item.musicFile.file)
        } catch (_: Exception) { null }
        if (!existingHash.isNullOrBlank()) {
            val krc = fetchAndParseKrcByHash(existingHash, title, musicDurSec)
            if (krc != null && krc.isWordByWord) {
                applySuccessKrc(item, existingHash, krc, title, artist, musicDurSec)
                return true
            }
        }

        
        val keywords = if (!artist.isNullOrBlank()) {
            listOf("$artist $title", "$title $artist")
        } else {
            listOf(title)
        }
        var songs: List<KugouApi.SongInfo> = emptyList()
        for (kw in keywords) {
            songs = KugouApi.searchSong(kw)
            if (songs.isNotEmpty()) break
        }

        if (songs.isEmpty()) {
            item.status = FileStatus.NO_MATCH
            item.detail = "搜索无结果"
            return false
        }

        
        val candidates = rankCandidates(songs, title, artist, musicDurSec, fileAlbum)
        if (candidates.isEmpty()) {
            item.status = FileStatus.NO_MATCH
            item.detail = "搜索到 ${songs.size} 首但无合适匹配"
            return false
        }

        var successHash: String? = null
        var successKrc: KrcParser.KrcLyric? = null
        var fallbackHash: String? = null
        var fallbackKrc: KrcParser.KrcLyric? = null

        
        val fileIsLive = hasLiveKeyword(title) || hasLiveKeyword(fileAlbum)

        for (c in candidates.take(10)) {
            val krc = fetchAndParseKrc(c) ?: continue
            if (krc.lines.isEmpty()) continue

            if (!krc.isWordByWord) {
                
                val lrcIsLive = hasLiveKeyword(krc.meta["ti"]) || hasLiveKeyword(krc.meta["al"])
                if (fallbackKrc == null && krc.lines.size >= 10 &&
                    !(lrcIsLive && !fileIsLive)) {
                    fallbackKrc = krc
                    fallbackHash = c.hash
                }
                continue
            }

            
            val byField = krc.meta["by"]?.lowercase() ?: ""
            val isAiGenerated = byField.contains("ai") || byField.contains("实验室") ||
                    byField.contains("自动生成")
            if (isAiGenerated) continue

            
            val metaOk = verifyKrcMeta(krc, title, artist, fileAlbum, allowRelax = false)
            if (!metaOk) {
                
                val lrcIsLive = hasLiveKeyword(krc.meta["ti"]) || hasLiveKeyword(krc.meta["al"])
                if (fallbackKrc == null && krc.lines.size >= 10 &&
                    !(lrcIsLive && !fileIsLive)) {
                    fallbackKrc = krc
                    fallbackHash = c.hash
                }
                continue
            }

            successHash = c.hash
            successKrc = krc
            break
        }

        if (successKrc == null) {
            if (fallbackKrc != null && fallbackKrc!!.isWordByWord) {
                successKrc = fallbackKrc
                successHash = fallbackHash
            } else {
                item.status = FileStatus.FETCH_FAILED
                item.detail = "前 ${minOf(10, candidates.size)} 个候选都拉不到逐字 KRC"
                return false
            }
        }

        applySuccessKrc(item, successHash!!, successKrc!!, title, artist, musicDurSec)
        return true
    }

    
    fun writeLyric(item: TaskItem): Boolean {
        val lrc = item.lrc
        if (lrc.isNullOrBlank()) {
            item.status = FileStatus.WRITE_FAILED
            item.detail = "歌词为空"
            return false
        }
        val hash = item.songHash ?: ""
        val content = LyricWriter.LyricsContent(lrc = lrc, songHash = hash)
        val result = try {
            LyricWriter.writeLyrics(item.musicFile.file, content)
        } catch (_: Exception) {
            item.status = FileStatus.WRITE_FAILED
            item.detail = "写入异常"
            return false
        }
        return when (result) {
            LyricWriter.WriteResult.SUCCESS -> {
                item.status = FileStatus.WRITTEN
                item.detail = "已写入 (逐字, ${item.songHash?.take(8) ?: ""})"
                true
            }
            LyricWriter.WriteResult.UNSUPPORTED_FORMAT -> {
                item.status = FileStatus.UNSUPPORTED_FORMAT
                item.detail = "不支持的格式: ${item.musicFile.ext}"
                false
            }
            LyricWriter.WriteResult.NO_LYRIC -> {
                item.status = FileStatus.WRITE_FAILED
                item.detail = "歌词内容为空"
                false
            }
            LyricWriter.WriteResult.WRITE_FAILED -> {
                item.status = FileStatus.WRITE_FAILED
                item.detail = "写入失败"
                false
            }
        }
    }

    
    private fun rankCandidates(
        songs: List<KugouApi.SongInfo>,
        title: String,
        artist: String?,
        musicDurSec: Long,
        fileAlbum: String?
    ): MutableList<KugouApi.SongInfo> {
        val artistNorm = normalizeArtist(artist)
        val titleNorm = normalize(title) ?: ""
        val fileHasMod = songnameHasModifier(title) || hasLiveKeyword(fileAlbum)

        val preferred = mutableListOf<KugouApi.SongInfo>()  
        val fallback = mutableListOf<KugouApi.SongInfo>()   

        for (s in songs) {
            val sg = normalizeArtist(s.singerName)
            val sn = normalize(s.songName) ?: ""

            
            if (artistNorm.isNotEmpty() && !artistMatch(artistNorm, sg)) {
                continue
            }

            
            val nameMatch = sn == titleNorm || titleNorm in sn || sn in titleNorm
            if (!nameMatch) continue

            
            val candHasMod = songnameHasModifier(s.songName) || hasLiveKeyword(s.albumName)
            if (candHasMod && !fileHasMod) {
                fallback.add(s)
                continue
            }

            
            if (fileHasMod && !candHasMod) {
                fallback.add(s)
                continue
            }

            preferred.add(s)
        }

        
        val result = if (preferred.isNotEmpty()) preferred else fallback

        
        result.sortBy { s ->
            val durDiff = if (s.durationSec > 0 && musicDurSec > 0) {
                kotlin.math.abs(s.durationSec - musicDurSec)
            } else 999L
            durDiff
        }

        return result
    }

    
    private fun fetchAndParseKrc(song: KugouApi.SongInfo): KrcParser.KrcLyric? {
        val cands = KugouApi.searchLyric(
            song.songName,
            song.durationSec * 1000,
            song.hash,
            song.albumAudioId
        )
        if (cands.isEmpty()) return null
        
        for (c in cands.take(3)) {
            val content = KugouApi.downloadLyric(c.id, c.accessKey, "krc") ?: continue
            val text = KugouApi.decodeKrc(content) ?: continue
            val krc = KrcParser.parse(text) ?: continue
            return krc
        }
        return null
    }

    
    private fun fetchAndParseKrcByHash(hash: String, title: String, musicDurSec: Long): KrcParser.KrcLyric? {
        val cands = KugouApi.searchLyric(title, musicDurSec * 1000, hash)
        if (cands.isEmpty()) return null
        for (c in cands.take(3)) {
            val content = KugouApi.downloadLyric(c.id, c.accessKey, "krc") ?: continue
            val text = KugouApi.decodeKrc(content) ?: continue
            val krc = KrcParser.parse(text) ?: continue
            return krc
        }
        return null
    }

    
    private fun applySuccessKrc(
        item: TaskItem,
        hash: String,
        krc: KrcParser.KrcLyric,
        title: String,
        artist: String?,
        musicDurSec: Long
    ) {
        val enhancedLrc = KrcParser.toEnhancedLrc(krc)

        item.songHash = hash
        item.lrc = enhancedLrc
        item.status = FileStatus.FETCHED
        item.detail = "匹配 hash=${hash.take(8)} (逐字, ${krc.lines.size} 行)"
    }

    
    private fun verifyKrcMeta(
        krc: KrcParser.KrcLyric,
        fileTitle: String,
        fileArtist: String?,
        fileAlbum: String?,
        allowRelax: Boolean
    ): Boolean {
        val lrcTi = normalize(krc.meta["ti"]) ?: return false
        val lrcAr = normalize(krc.meta["ar"]) ?: ""
        val lrcAl = normalize(krc.meta["al"]) ?: ""

        
        val normFileTitle = normalize(fileTitle) ?: return false
        val tiMatch = normFileTitle == lrcTi ||
                normFileTitle in lrcTi || lrcTi in normFileTitle ||
                
                stripParens(normFileTitle) == stripParens(lrcTi)
        if (!tiMatch) return false

        
        val fileIsLive = hasLiveKeyword(fileTitle) || hasLiveKeyword(fileAlbum)
        val lrcIsLive = hasLiveKeyword(krc.meta["ti"]) || hasLiveKeyword(krc.meta["al"])
        if (lrcIsLive && !fileIsLive) return false

        
        if (!allowRelax && fileArtist != null) {
            val normFileArtist = normalizeArtist(fileArtist)
            val normLrcAr = normalizeArtist(lrcAr)
            if (normFileArtist.isNotEmpty()) {
                if (!artistMatch(normFileArtist, normLrcAr)) return false
            }
        }

        return true
    }

    
    private fun stripParens(s: String): String {
        var r = s
        r = Regex("[\\(（].*?[\\)）]").replace(r, "")
        r = r.replace(" ", "")
        return r
    }

    
    private val LIVE_KEYWORDS = listOf("live", "演唱会", "现场", "concert")
    private val MODIFIER_KEYWORDS = listOf(
        "remix", "skr", "混音", "cover", "翻唱", "伴奏", "纯音乐",
        "钢琴版", "吉他版", "加速", "减速", "片段", "demo", "remake",
        "清唱版", "acoustic", "live", "现场", "演唱会", "dj"
    )

    
    private fun normalize(s: String?): String? {
        if (s.isNullOrBlank()) return null
        var r = s
        r = Regex("[\\(（].*?[\\)）]").replace(r, "")
        r = Regex("\\[.*?]").replace(r, "")
        r = Regex("【.*?】").replace(r, "")
        
        r = java.text.Normalizer.normalize(r, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        r = r.replace(" ", "").lowercase()
        return r.trim().ifBlank { null }
    }

    
    private fun normalizeArtist(s: String?): String {
        val n = normalize(s) ?: return ""
        var r = n
        
        val seps = listOf("&", "、", "/", ",", "+", "and", "feat", "ft", "with")
        for (sep in seps) {
            r = r.replace(sep, ";")
        }
        
        r = Regex(";+").replace(r, ";").trim(';')
        return r
    }

    
    private fun artistMatch(fileArtist: String, lrcArtist: String): Boolean {
        if (fileArtist.isEmpty()) return true
        if (fileArtist == lrcArtist) return true
        if (fileArtist in lrcArtist || lrcArtist in fileArtist) return true
        
        val fileParts = fileArtist.split(";").filter { it.isNotEmpty() }
        val lrcParts = lrcArtist.split(";").filter { it.isNotEmpty() }
        return fileParts.any { fa ->
            lrcParts.any { la -> fa == la || fa in la || la in fa }
        }
    }

    
    private fun songnameHasModifier(songname: String): Boolean {
        val sn = songname.lowercase()
        
        Regex("[\\(（]([^)）]*)[\\)）]").findAll(songname).forEach { m ->
            val content = m.groupValues[1].lowercase()
            if (MODIFIER_KEYWORDS.any { it in content }) return true
        }
        return MODIFIER_KEYWORDS.any { it in sn }
    }

    
    private fun hasLiveKeyword(s: String?): Boolean {
        if (s.isNullOrBlank()) return false
        val sl = s.lowercase()
        return LIVE_KEYWORDS.any { it in sl }
    }
}
