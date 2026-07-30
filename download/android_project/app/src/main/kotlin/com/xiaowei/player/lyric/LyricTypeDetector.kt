package com.xiaowei.player.lyric


object LyricTypeDetector {

    enum class LyricType {
        NEED_REPLACE,  
        ALREADY_OK,    
        NONE           
    }

    
    fun detect(lyricsText: String?, lyricSource: String? = null): LyricType {
        if (lyricsText.isNullOrBlank()) return LyricType.NONE

        
        val lineTimestampRe = Regex("\\[\\d{2}:\\d{2}[.:.]?\\d{0,3}\\]")
        if (!lineTimestampRe.containsMatchIn(lyricsText)) return LyricType.NONE

        
        return if (lyricSource.equals("kugou-krc", ignoreCase = true)) {
            LyricType.ALREADY_OK
        } else {
            LyricType.NEED_REPLACE
        }
    }
}
