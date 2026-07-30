package com.xiaowei.player.lyric


object KrcParser {

    data class KrcLyric(
        val meta: Map<String, String>,
        val lines: List<KrcLine>,
        val isWordByWord: Boolean
    )

    data class KrcLine(
        val startMs: Long,
        val rawTimestamp: String,
        val words: List<KrcWord>
    )

    data class KrcWord(
        val char: String,
        val startMs: Long,
        val durationMs: Long
    )

    
    fun parse(krcText: String): KrcLyric? {
        if (krcText.isBlank()) return null

        val meta = mutableMapOf<String, String>()
        var hasWordTimestamp = false

        
        val metaRe = Regex("\\[(ti|ar|al|by|offset|hash|total|sign|qq|language|id):([^]]*)]")
        var text = metaRe.replace(krcText) { m ->
            meta[m.groupValues[1]] = m.groupValues[2].trim()
            ""
        }

        
        val lineRe = Regex("\\[(\\d+),(\\d+)]")
        data class LineHead(val start: Int, val end: Int, val startMs: Long)
        val heads = mutableListOf<LineHead>()
        lineRe.findAll(text).forEach { m ->
            val startMs = m.groupValues[1].toLongOrNull() ?: return@forEach
            heads.add(LineHead(m.range.first, m.range.last + 1, startMs))
        }

        if (heads.isEmpty()) return null

        
        heads.sortBy { it.start }

        
        
        
        
        
        val wordRe = Regex("<(\\d+),(\\d+),\\d+>([^<]*)")
        val lines = mutableListOf<KrcLine>()

        for (i in heads.indices) {
            val head = heads[i]
            val contentEnd = if (i + 1 < heads.size) heads[i + 1].start else text.length
            val afterTs = text.substring(head.end, contentEnd)

            val words = mutableListOf<KrcWord>()
            wordRe.findAll(afterTs).forEach { w ->
                val offsetMs = w.groupValues[1].toLongOrNull() ?: return@forEach
                val durMs = w.groupValues[2].toLongOrNull() ?: return@forEach
                val charStr = w.groupValues[3]
                val wordStartMs = head.startMs + offsetMs
                if (charStr.isNotEmpty() || offsetMs > 0) {
                    words.add(KrcWord(charStr, wordStartMs, durMs))
                    if (durMs > 0) hasWordTimestamp = true
                }
            }

            if (words.isNotEmpty()) {
                val rawTs = formatLineTimestamp(head.startMs)
                lines.add(KrcLine(head.startMs, rawTs, words))
            }
        }

        if (lines.isEmpty()) return null
        return KrcLyric(meta, lines, hasWordTimestamp)
    }

    
    fun toEnhancedLrc(krc: KrcLyric): String {
        val sb = StringBuilder()

        krc.meta["ti"]?.let { sb.append("[ti:$it]\n") }
        krc.meta["ar"]?.let { sb.append("[ar:$it]\n") }
        krc.meta["al"]?.let { sb.append("[al:$it]\n") }
        val offset = krc.meta["offset"]
        if (!offset.isNullOrBlank()) sb.append("[offset:$offset]\n")

        for (line in krc.lines) {
            if (line.words.isEmpty()) continue
            sb.append(line.rawTimestamp)
            for (w in line.words) {
                sb.append(formatWordTimestamp(w.startMs))
                sb.append(w.char)
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    
    private fun formatLineTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        val xxx = ms % 1000
        return "[%02d:%02d.%03d]".format(mm, ss, xxx)
    }

    
    private fun formatWordTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        val xxx = ms % 1000
        return "<%02d:%02d.%03d>".format(mm, ss, xxx)
    }
}
