package com.xiaowei.player.data

import android.net.Uri

private fun isInvalidMetadata(s: String?): Boolean {
    if (s.isNullOrBlank()) return true
    val trimmed = s.trim()
    if (trimmed.isEmpty()) return true
    val lower = trimmed.lowercase()
    if (lower.startsWith("<") && lower.endsWith(">")) return true
    if (lower == "unknown" || lower == "none" || lower == "null") return true
    if (lower == "music") return true
    return false
}

@Suppress("unused")
private fun normalizeMetadata(s: String?, fallback: String): String =
    if (isInvalidMetadata(s)) fallback else s!!.trim()

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val albumArtist: String? = null,
    val duration: Long,
    val data: String,
    val dateAdded: Long,
    val track: Int = 0,
    val year: Int = 0,
    val genre: String? = null,
    val albumArtUri: Uri? = null,
    val lyrics: String? = null,
    val mimeType: String? = null,
    val size: Long = 0,

    val source: String = "mediastore"
) {
    val hasLyrics: Boolean get() = !lyrics.isNullOrBlank()

    val displayArtist: String
        get() = if (isInvalidMetadata(artist)) com.xiaowei.player.i18n.Strings.get("unknown_artist") else artist.trim()
    val displayAlbum: String
        get() = if (isInvalidMetadata(album)) com.xiaowei.player.i18n.Strings.get("unknown_album") else album.trim()

    val displayAlbumDashArtist: String
        get() = "${displayAlbum}-${displayArtist}"
}

data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val albumArtUri: Uri? = null,
    val totalDuration: Long = 0,

    val firstSongData: String? = null
) {
    val displayName: String
        get() = if (isInvalidMetadata(name)) com.xiaowei.player.i18n.Strings.get("unknown_artist") else name.trim()
}

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val year: Int,
    val songCount: Int,
    val albumArtUri: Uri? = null,
    val totalDuration: Long = 0,
    val firstSongData: String? = null
) {
    val displayName: String
        get() = if (isInvalidMetadata(name)) com.xiaowei.player.i18n.Strings.get("unknown_album") else name.trim()
    val displayArtist: String
        get() = if (isInvalidMetadata(artist)) com.xiaowei.player.i18n.Strings.get("unknown_artist") else artist.trim()

    val displayAlbumDashArtist: String
        get() = "${displayName}-${displayArtist}"
}

data class LyricWord(
    val timeMs: Long,
    val text: String
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
) {
    val isWordByWord: Boolean get() = words.isNotEmpty()
}

data class RecommendCard(
    val title: String,
    val subtitle: String,
    val coverUri: Uri?,
    val songs: List<Song>
)
