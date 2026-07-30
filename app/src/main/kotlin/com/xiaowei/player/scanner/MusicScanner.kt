package com.xiaowei.player.scanner

import android.media.MediaMetadataRetriever
import java.io.File


object MusicScanner {

    private val SUPPORTED_EXT = setOf("flac", "mp3", "m4a", "ogg", "ape", "wav")

    data class MusicFile(
        val file: File,
        val name: String,        
        val ext: String,         
        val sizeBytes: Long,
        val durationMs: Long,    
        
        val metaTitle: String? = null,
        val metaArtist: String? = null,
        val metaAlbum: String? = null
    )

    fun scan(dir: File): List<MusicFile> {
        if (!dir.isDirectory) return emptyList()
        val result = mutableListOf<MusicFile>()
        dir.walkTopDown()
            .filter { it.isFile }
            .forEach { f ->
                val ext = f.extension.lowercase()
                if (ext in SUPPORTED_EXT) {
                    val (dur, title, artist, album) = readMetadata(f)
                    result.add(MusicFile(f, f.nameWithoutExtension, ext, f.length(), dur, title, artist, album))
                }
            }
        return result.sortedBy { it.name }
    }

    
    private fun readMetadata(file: File): MetadataResult {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
            MetadataResult(ms, title, artist, album)
        } catch (_: Exception) {
            MetadataResult(0L, null, null, null)
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    private data class MetadataResult(
        val durationMs: Long,
        val title: String?,
        val artist: String?,
        val album: String?
    )

    
    fun guessTitleArtist(mf: MusicFile): Pair<String, String?> {
        val metaTitle = mf.metaTitle?.takeIf { it.isNotBlank() }
        val metaArtist = mf.metaArtist?.takeIf { it.isNotBlank() }
        if (metaTitle != null) return metaTitle to metaArtist

        val n = mf.name
        val parts = n.split(" - ", " -", "- ", "-").map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> parts[0] to parts[1]
            else -> n to null
        }
    }
}
