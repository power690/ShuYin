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

        val inlineWordRe = Regex("\\[\\d{1,3}:\\d{1,2}[.:]?\\d{0,3}]\\S+\\[\\d{1,3}:\\d{1,2}")
        if (inlineWordRe.containsMatchIn(lyricsText)) return LyricType.ALREADY_OK

        return if (lyricSource.equals("kugou-krc", ignoreCase = true)) {
            LyricType.ALREADY_OK
        } else {
            LyricType.NEED_REPLACE
        }
    }
}
