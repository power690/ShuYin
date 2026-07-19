package com.xiaowei.player.data

object LyricsParser {

    private val timeTagRegex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val metaTagRegex = Regex("""\[(ti|ar|al|by|offset):(.*)]""", RegexOption.IGNORE_CASE)

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()

        val lines = raw.split('\n', '\r').filter { it.isNotBlank() }
        val hasTimeTag = lines.any { timeTagRegex.containsMatchIn(it) }
        if (!hasTimeTag) {

            return lines.mapIndexed { i, line ->
                LyricLine(timeMs = -1L, text = line.trim())
            }
        }

        val result = mutableListOf<LyricLine>()
        var offset = 0L

        for (line in lines) {
            metaTagRegex.findAll(line).forEach { m ->
                val key = m.groupValues[1].lowercase()
                val value = m.groupValues[2].trim()
                if (key == "offset") {
                    value.toLongOrNull()?.let { offset = it }
                }
            }
        }

        for (line in lines) {
            val matches = timeTagRegex.findAll(line).toList()
            if (matches.isEmpty()) continue
            val contentStart = matches.last().range.last + 1
            val text = line.substring(contentStart).trim()
            for (m in matches) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val msPart = m.groupValues[3]
                val ms = when {
                    msPart.isBlank() -> 0L
                    msPart.length == 1 -> msPart.toLong() * 100
                    msPart.length == 2 -> msPart.toLong() * 10
                    else -> msPart.take(3).toLong()
                }
                val t = min * 60_000 + sec * 1000 + ms + offset
                result.add(LyricLine(timeMs = t.coerceAtLeast(0), text = text))
            }
        }
        return result.sortedBy { it.timeMs }
    }

    fun findCurrentLine(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (lines.all { it.timeMs < 0 }) {

            val perLine = 4000L
            val idx = (positionMs / perLine).toInt().coerceIn(0, lines.lastIndex)
            return idx
        }
        var low = 0
        var high = lines.lastIndex
        var ans = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                ans = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return ans
    }
}
