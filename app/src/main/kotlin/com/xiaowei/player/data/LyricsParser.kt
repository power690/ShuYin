package com.xiaowei.player.data

object LyricsParser {

    private val timeTagRegex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val metaTagRegex = Regex("""\[(ti|ar|al|by|offset):(.*)]""", RegexOption.IGNORE_CASE)
    private val wordTagRegex = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

    private fun isInlineWordLine(matches: List<MatchResult>, line: String): Boolean {
        if (matches.size < 2) return false
        for (i in 0 until matches.size - 1) {
            val between = line.substring(matches[i].range.last + 1, matches[i + 1].range.first)
            if (between.isNotBlank()) return true
        }
        return false
    }

    private fun parseTimestamp(min: String, sec: String, msPart: String): Long {
        val minL = min.toLong()
        val secL = sec.toLong()
        val ms = when {
            msPart.isBlank() -> 0L
            msPart.length == 1 -> msPart.toLong() * 100
            msPart.length == 2 -> msPart.toLong() * 10
            else -> msPart.take(3).toLong()
        }
        return minL * 60_000 + secL * 1000 + ms
    }

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

            if (isInlineWordLine(matches, line)) {
                val words = mutableListOf<LyricWord>()
                val textBuilder = StringBuilder()
                for (i in matches.indices) {
                    val segStart = matches[i].range.last + 1
                    val segEnd = if (i + 1 < matches.size) matches[i + 1].range.first else line.length
                    if (segEnd <= segStart) continue
                    val segText = line.substring(segStart, segEnd)
                    if (segText.isEmpty()) continue
                    val wt = parseTimestamp(
                        matches[i].groupValues[1], matches[i].groupValues[2], matches[i].groupValues[3]
                    ) + offset
                    words.add(LyricWord(timeMs = wt.coerceAtLeast(0), text = segText))
                    textBuilder.append(segText)
                }
                val inlineText = textBuilder.toString()
                if (inlineText.isNotBlank() && words.isNotEmpty()) {
                    val lineTime = parseTimestamp(
                        matches[0].groupValues[1], matches[0].groupValues[2], matches[0].groupValues[3]
                    ) + offset
                    result.add(
                        LyricLine(
                            timeMs = lineTime.coerceAtLeast(0),
                            text = inlineText,
                            words = words
                        )
                    )
                }
                continue
            }

            val contentStart = matches.last().range.last + 1
            val content = line.substring(contentStart)

            val wordMatches = wordTagRegex.findAll(content).toList()
            if (wordMatches.isEmpty()) {
                val text = content.trim()
                for (m in matches) {
                    val t = parseTimestamp(
                        m.groupValues[1], m.groupValues[2], m.groupValues[3]
                    ) + offset
                    result.add(LyricLine(timeMs = t.coerceAtLeast(0), text = text))
                }
            } else {
                val words = mutableListOf<LyricWord>()
                val textBuilder = StringBuilder()
                for ((i, wm) in wordMatches.withIndex()) {
                    val wordStart = wm.range.last + 1
                    val wordEnd = if (i + 1 < wordMatches.size) wordMatches[i + 1].range.first else content.length
                    val wordText = content.substring(wordStart, wordEnd)
                    if (wordText.isNotEmpty()) {
                        val wt = parseTimestamp(
                            wm.groupValues[1], wm.groupValues[2], wm.groupValues[3]
                        ) + offset
                        words.add(LyricWord(timeMs = wt.coerceAtLeast(0), text = wordText))
                        textBuilder.append(wordText)
                    }
                }
                val text = textBuilder.toString().trim()
                for (m in matches) {
                    val t = parseTimestamp(
                        m.groupValues[1], m.groupValues[2], m.groupValues[3]
                    ) + offset
                    result.add(
                        LyricLine(
                            timeMs = t.coerceAtLeast(0),
                            text = text,
                            words = words
                        )
                    )
                }
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

    fun findCurrentWordIndex(words: List<LyricWord>, positionMs: Long): Int {
        if (words.isEmpty()) return -1
        var low = 0
        var high = words.lastIndex
        var ans = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (words[mid].timeMs <= positionMs) {
                ans = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return ans
    }
}
