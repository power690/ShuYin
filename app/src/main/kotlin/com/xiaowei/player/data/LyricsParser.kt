package com.xiaowei.player.data

object LyricsParser {

    private val timeTagRegex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val metaTagRegex = Regex("""\[(ti|ar|al|by|offset):(.*)]""", RegexOption.IGNORE_CASE)
    private val wordTagRegex = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val qrcWordTagRegex = Regex("""<(-?\d{1,7})(?:[,.](-?\d{1,7}))?>""")

    private class WordTag(
        val startIdx: Int,
        val endIdx: Int,
        val isColon: Boolean,
        val absTimeMs: Long,
        val relTimeMs: Long
    )

    private fun collectWordTags(content: String): List<WordTag> {
        val tags = mutableListOf<WordTag>()
        for (m in wordTagRegex.findAll(content)) {
            val t = parseTimestamp(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            tags.add(WordTag(m.range.first, m.range.last + 1, true, t, 0L))
        }
        for (m in qrcWordTagRegex.findAll(content)) {
            if (m.value.contains(':')) continue
            val rel = m.groupValues[1].toLongOrNull() ?: continue
            tags.add(WordTag(m.range.first, m.range.last + 1, false, 0L, rel))
        }
        return tags.sortedBy { it.startIdx }
    }

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
        if (TtmlParser.looksLikeTtml(raw)) return TtmlParser.parse(raw)

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
                    ) - offset
                    words.add(LyricWord(timeMs = wt.coerceAtLeast(0), text = segText))
                    textBuilder.append(segText)
                }
                val inlineText = textBuilder.toString()
                if (inlineText.isNotBlank() && words.isNotEmpty()) {
                    val lineTime = parseTimestamp(
                        matches[0].groupValues[1], matches[0].groupValues[2], matches[0].groupValues[3]
                    ) - offset
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

            val wordTags = collectWordTags(content)
            if (wordTags.isEmpty()) {
                val text = content.trim()
                for (m in matches) {
                    val t = parseTimestamp(
                        m.groupValues[1], m.groupValues[2], m.groupValues[3]
                    ) - offset
                    result.add(LyricLine(timeMs = t.coerceAtLeast(0), text = text))
                }
            } else {
                val lineTimeMs = (parseTimestamp(
                    matches[0].groupValues[1], matches[0].groupValues[2], matches[0].groupValues[3]
                ) - offset).coerceAtLeast(0L)
                val words = mutableListOf<LyricWord>()
                val textBuilder = StringBuilder()
                var cursor = 0
                var lastTime = -1L
                for ((i, wt) in wordTags.withIndex()) {
                    if (wt.startIdx > cursor) {
                        val wordText = content.substring(cursor, wt.startIdx)
                        var t = when {
                            i > 0 && wordTags[i - 1].isColon -> wordTags[i - 1].absTimeMs - offset
                            !wt.isColon -> if (wt.relTimeMs in 0..12000L) {
                                lineTimeMs + wt.relTimeMs
                            } else {
                                wt.relTimeMs.coerceAtLeast(0L)
                            }
                            else -> -1L
                        }
                        if (lastTime >= 0 && t < lastTime) t = lastTime
                        if (t < 0) t = lineTimeMs
                        words.add(LyricWord(timeMs = t, text = wordText))
                        textBuilder.append(wordText)
                        lastTime = t
                    }
                    cursor = wt.endIdx
                }
                if (cursor < content.length) {
                    val wordText = content.substring(cursor)
                    val t = if (wordTags.last().isColon) {
                        (wordTags.last().absTimeMs - offset).coerceAtLeast(0L)
                    } else if (lastTime >= 0) {
                        lastTime
                    } else {
                        lineTimeMs
                    }
                    words.add(LyricWord(timeMs = t, text = wordText))
                    textBuilder.append(wordText)
                }
                val text = textBuilder.toString().trim()
                for (m in matches) {
                    val t = parseTimestamp(
                        m.groupValues[1], m.groupValues[2], m.groupValues[3]
                    ) - offset
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
        return result.sortedWith(compareBy({ it.timeMs }, { if (isMarkerLine(it)) 0 else 1 }))
    }

    private fun isMarkerLine(line: LyricLine): Boolean {
        val t = line.text
        return t.length <= 3 && (t.endsWith("：") || t.endsWith(":"))
    }

    fun findCurrentLine(lines: List<LyricLine>, positionMs: Long, leadMs: Long = 200L): Int {
        if (lines.isEmpty()) return -1
        if (lines.all { it.timeMs < 0 }) {

            val perLine = 4000L
            val idx = (positionMs / perLine).toInt().coerceIn(0, lines.lastIndex)
            return idx
        }
        val seekMs = positionMs + leadMs
        var low = 0
        var high = lines.lastIndex
        var ans = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= seekMs) {
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
