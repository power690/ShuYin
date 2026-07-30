package com.xiaowei.player

import com.xiaowei.player.lyric.BatchProcessor
import com.xiaowei.player.lyric.LyricWriter
import com.xiaowei.player.lyric.LyricTypeDetector
import com.xiaowei.player.scanner.MusicScanner
import java.io.File


object LyricFacade {

    
    enum class Status {
        
        WRITTEN,
        
        ALREADY_WORD_BY_WORD,
        
        NO_MATCH,
        
        FETCH_FAILED,
        
        WRITE_FAILED,
        
        UNSUPPORTED_FORMAT
    }

    
    data class Result(
        val status: Status,
        
        val detail: String = "",
        
        val lrc: String? = null,
        
        val songHash: String? = null
    )

    
    fun processFile(file: File): Result {
        if (!file.isFile) return Result(Status.FETCH_FAILED, "文件不存在")

        
        val mf = MusicScanner.scan(file.parentFile ?: return Result(Status.FETCH_FAILED, "无法获取父目录"))
            .firstOrNull { it.file.absolutePath == file.absolutePath }
            ?: return Result(Status.UNSUPPORTED_FORMAT, "文件不在支持列表内")

        val item = BatchProcessor.TaskItem(mf, BatchProcessor.FileStatus.NO_LYRIC)

        
        val existing = try { LyricWriter.readLyrics(file) } catch (_: Exception) { null }
        val source = try { LyricWriter.readLyricSource(file) } catch (_: Exception) { null }
        val type = LyricTypeDetector.detect(existing, source)
        item.status = when (type) {
            LyricTypeDetector.LyricType.ALREADY_OK -> BatchProcessor.FileStatus.ALREADY_WORD_BY_WORD
            LyricTypeDetector.LyricType.NEED_REPLACE -> BatchProcessor.FileStatus.FIXED_LYRIC
            LyricTypeDetector.LyricType.NONE -> BatchProcessor.FileStatus.NO_LYRIC
        }

        if (item.status == BatchProcessor.FileStatus.ALREADY_WORD_BY_WORD) {
            return Result(Status.ALREADY_WORD_BY_WORD, "已是逐字歌词，跳过", existing)
        }

        
        if (!BatchProcessor.fetchLyric(item)) {
            val status = when (item.status) {
                BatchProcessor.FileStatus.NO_MATCH -> Status.NO_MATCH
                BatchProcessor.FileStatus.FETCH_FAILED -> Status.FETCH_FAILED
                BatchProcessor.FileStatus.UNSUPPORTED_FORMAT -> Status.UNSUPPORTED_FORMAT
                else -> Status.FETCH_FAILED
            }
            return Result(status, item.detail, item.lrc, item.songHash)
        }

        
        if (!BatchProcessor.writeLyric(item)) {
            val status = when (item.status) {
                BatchProcessor.FileStatus.UNSUPPORTED_FORMAT -> Status.UNSUPPORTED_FORMAT
                else -> Status.WRITE_FAILED
            }
            return Result(status, item.detail, item.lrc, item.songHash)
        }

        return Result(Status.WRITTEN, item.detail, item.lrc, item.songHash)
    }

    
    fun processDirectory(dir: File): List<Pair<File, Result>> {
        if (!dir.isDirectory) return emptyList()
        val items = BatchProcessor.scanAndDetect(dir)
        return items.map { item ->
            val result = if (item.status == BatchProcessor.FileStatus.ALREADY_WORD_BY_WORD) {
                Result(Status.ALREADY_WORD_BY_WORD, item.detail)
            } else {
                val fetched = BatchProcessor.fetchLyric(item)
                if (!fetched) {
                    val status = when (item.status) {
                        BatchProcessor.FileStatus.NO_MATCH -> Status.NO_MATCH
                        BatchProcessor.FileStatus.UNSUPPORTED_FORMAT -> Status.UNSUPPORTED_FORMAT
                        else -> Status.FETCH_FAILED
                    }
                    Result(status, item.detail, item.lrc, item.songHash)
                } else {
                    if (!BatchProcessor.writeLyric(item)) {
                        val status = when (item.status) {
                            BatchProcessor.FileStatus.UNSUPPORTED_FORMAT -> Status.UNSUPPORTED_FORMAT
                            else -> Status.WRITE_FAILED
                        }
                        Result(status, item.detail, item.lrc, item.songHash)
                    } else {
                        Result(Status.WRITTEN, item.detail, item.lrc, item.songHash)
                    }
                }
            }
            item.musicFile.file to result
        }
    }

    
    fun fetchLyric(
        title: String,
        artist: String?,
        durationMs: Long,
        album: String? = null
    ): String? {
        val fakeFile = MusicScanner.MusicFile(
            file = File("/tmp/${title}_${artist ?: ""}.fake"),
            name = if (artist.isNullOrBlank()) title else "$title - $artist",
            ext = "flac",
            sizeBytes = 0,
            durationMs = durationMs,
            metaTitle = title,
            metaArtist = artist,
            metaAlbum = album
        )
        val item = BatchProcessor.TaskItem(fakeFile, BatchProcessor.FileStatus.NO_LYRIC)
        return if (BatchProcessor.fetchLyric(item)) item.lrc else null
    }

    
    fun writeLyric(file: File, lrc: String, songHash: String? = null): Boolean {
        val content = LyricWriter.LyricsContent(lrc = lrc, songHash = songHash)
        return LyricWriter.writeLyrics(file, content) == LyricWriter.WriteResult.SUCCESS
    }

    
    fun scanDirectory(dir: File): List<MusicScanner.MusicFile> {
        return MusicScanner.scan(dir)
    }

    
    fun readLyrics(file: File): String? {
        return try { LyricWriter.readLyrics(file) } catch (_: Exception) { null }
    }

    
    fun detectLyricType(file: File): LyricTypeDetector.LyricType {
        val existing = try { LyricWriter.readLyrics(file) } catch (_: Exception) { null }
        val source = try { LyricWriter.readLyricSource(file) } catch (_: Exception) { null }
        return LyricTypeDetector.detect(existing, source)
    }
}
