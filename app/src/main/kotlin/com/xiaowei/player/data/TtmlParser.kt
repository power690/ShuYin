package com.xiaowei.player.data

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

object TtmlParser {

    private class Rates(val frameRate: Double, val subFrameRate: Double, val tickRate: Double)

    private class Token(val text: String, val timeMs: Long?)

    private class Collector(var lastEndMs: Long? = null)

    private val rootStartRe = Regex("^<tt[\\s>]")
    private val rootAnywhereRe = Regex("<tt[\\s>]")
    private val ttmlNsRe = Regex("""xmlns[^\s]*ttml""", RegexOption.IGNORE_CASE)
    private val offsetTimeRe = Regex("""^(\d+(?:\.\d+)?)(ms|s|m|h|f|t)?$""")

    fun looksLikeTtml(raw: String): Boolean {
        if (raw.isBlank()) return false
        val t = raw.trim().removePrefix("\uFEFF")
        if (rootStartRe.containsMatchIn(t)) return true
        if (t.startsWith("<?xml") && rootAnywhereRe.containsMatchIn(t)) return true
        if (ttmlNsRe.containsMatchIn(t)) return true
        return false
    }

    fun parse(raw: String): List<LyricLine> {
        val text = raw.removePrefix("\uFEFF")
        if (text.isBlank()) return emptyList()
        return try {
            val doc = newDocument(text)
            val root = doc.documentElement ?: return emptyList()
            val rates = readRates(root)
            val out = mutableListOf<LyricLine>()
            collectBlock(root, rates, out, Collector())
            out.sortedBy { it.timeMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun newDocument(text: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (_: Exception) {
        }
        factory.isNamespaceAware = false
        val bytes = text.trim().toByteArray(Charsets.UTF_8)
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun readRates(root: Element): Rates {
        var frameRate = 30.0
        var subFrameRate = 1.0
        var tickRate = 1.0
        val attrs = root.attributes
        for (i in 0 until attrs.length) {
            val item = attrs.item(i)
            when (localName(item.nodeName)) {
                "frameRate" -> item.nodeValue.trim().toDoubleOrNull()?.let { if (it > 0.0) frameRate = it }
                "subFrameRate" -> item.nodeValue.trim().toDoubleOrNull()?.let { if (it > 0.0) subFrameRate = it }
                "tickRate" -> item.nodeValue.trim().toDoubleOrNull()?.let { if (it > 0.0) tickRate = it }
            }
        }
        return Rates(frameRate, subFrameRate, tickRate)
    }

    private fun localName(name: String): String {
        val idx = name.indexOf(':')
        return if (idx >= 0) name.substring(idx + 1) else name
    }

    private fun collectBlock(node: Node, rates: Rates, out: MutableList<LyricLine>, state: Collector) {
        var child = node.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                when (localName(child.nodeName)) {
                    "p" -> {
                        val begin = timeAttr(child, "begin", rates)
                        val lineBegin = begin ?: state.lastEndMs
                        appendLine(child, lineBegin, rates, out)
                        val end = timeAttr(child, "end", rates)
                        if (end != null) {
                            state.lastEndMs = end
                        } else {
                            val dur = timeAttr(child, "dur", rates)
                            if (dur != null && lineBegin != null) {
                                state.lastEndMs = lineBegin + dur
                            }
                        }
                    }
                    "div", "body" -> collectBlock(child, rates, out, state)
                }
            }
            child = child.nextSibling
        }
    }

    private fun appendLine(p: Node, lineBegin: Long?, rates: Rates, out: MutableList<LyricLine>) {
        val tokens = mutableListOf<Token>()
        collectInline(p, rates, tokens)
        val words = tokensToWords(tokens, lineBegin)
        if (words.isEmpty()) {
            val text = tokens.joinToString("") { it.text }.trim()
            if (text.isEmpty()) return
            out.add(LyricLine(timeMs = lineBegin ?: 0L, text = text))
        } else {
            val text = words.joinToString("") { it.text }
            if (text.isBlank()) return
            out.add(LyricLine(timeMs = lineBegin ?: words.first().timeMs, text = text, words = words))
        }
    }

    private fun collectInline(node: Node, rates: Rates, tokens: MutableList<Token>) {
        var child = node.firstChild
        while (child != null) {
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    appendToken(tokens, Token(child.nodeValue ?: "", null))
                }
                Node.ELEMENT_NODE -> when (localName(child.nodeName)) {
                    "span" -> {
                        val begin = timeAttr(child, "begin", rates)
                        if (begin != null) {
                            appendToken(tokens, Token(textOf(child), begin))
                        } else {
                            collectInline(child, rates, tokens)
                        }
                    }
                    "br" -> appendToken(tokens, Token("\n", null))
                }
            }
            child = child.nextSibling
        }
    }

    private fun appendToken(tokens: MutableList<Token>, token: Token) {
        if (token.text.isEmpty()) return
        val last = tokens.lastOrNull()
        if (last != null && last.timeMs == null && token.timeMs == null) {
            tokens[tokens.size - 1] = Token(last.text + token.text, null)
        } else {
            tokens.add(token)
        }
    }

    private fun textOf(node: Node): String {
        val sb = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> sb.append(child.nodeValue ?: "")
                Node.ELEMENT_NODE ->
                    if (localName(child.nodeName) == "br") {
                        sb.append('\n')
                    } else {
                        sb.append(textOf(child))
                    }
            }
            child = child.nextSibling
        }
        return sb.toString()
    }

    private fun tokensToWords(tokens: List<Token>, lineBegin: Long?): List<LyricWord> {
        val raw = tokens.filter { it.text.isNotEmpty() }
        if (raw.isEmpty()) return emptyList()
        if (raw.none { it.timeMs != null }) return emptyList()
        val words = mutableListOf<LyricWord>()
        for (t in raw) {
            val timeMs = t.timeMs
            if (timeMs != null) {
                words.add(LyricWord(timeMs, t.text))
            } else if (words.isEmpty()) {
                words.add(LyricWord(lineBegin ?: 0L, t.text))
            } else {
                val last = words.removeAt(words.size - 1)
                words.add(LyricWord(last.timeMs, last.text + t.text))
            }
        }
        trimEdges(words)
        return words
    }

    private fun trimEdges(words: MutableList<LyricWord>) {
        while (words.isNotEmpty() && words.first().text.isBlank()) words.removeAt(0)
        while (words.isNotEmpty() && words.last().text.isBlank()) words.removeAt(words.size - 1)
        if (words.isEmpty()) return
        words[0] = LyricWord(words[0].timeMs, words[0].text.trimStart())
        val lastIdx = words.size - 1
        words[lastIdx] = LyricWord(words[lastIdx].timeMs, words[lastIdx].text.trimEnd())
    }

    private fun timeAttr(node: Node, name: String, rates: Rates): Long? {
        val attrs = node.attributes ?: return null
        for (i in 0 until attrs.length) {
            val item = attrs.item(i)
            if (localName(item.nodeName) == name) {
                return parseTime(item.nodeValue, rates)
            }
        }
        return null
    }

    private fun fractionToMs(frac: String): Long? {
        if (frac.isEmpty()) return null
        val digits = frac.take(3).padEnd(3, '0')
        return digits.toLongOrNull()
    }

    private fun parseTime(value: String, rates: Rates): Long? {
        val v = value.trim()
        if (v.isEmpty() || v.startsWith("-")) return null
        val colonCount = v.count { it == ':' }
        if (colonCount >= 2) {
            val parts = v.split(':')
            if (parts.size == 3) {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val secPart = parts[2]
                val dot = secPart.indexOf('.')
                if (dot >= 0) {
                    val sec = secPart.substring(0, dot).toLongOrNull() ?: return null
                    val frac = fractionToMs(secPart.substring(dot + 1)) ?: return null
                    return h * 3_600_000L + m * 60_000L + sec * 1000L + frac
                }
                val sec = secPart.toLongOrNull() ?: return null
                return h * 3_600_000L + m * 60_000L + sec * 1000L
            }
            if (parts.size == 4) {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val sec = parts[2].toLongOrNull() ?: return null
                val framePart = parts[3]
                var frames = 0.0
                var subFrames = 0.0
                val dot = framePart.indexOf('.')
                if (dot >= 0) {
                    frames = framePart.substring(0, dot).toDoubleOrNull() ?: return null
                    subFrames = framePart.substring(dot + 1).toDoubleOrNull() ?: 0.0
                } else {
                    frames = framePart.toDoubleOrNull() ?: return null
                }
                val baseMs = h * 3_600_000L + m * 60_000L + sec * 1000L
                val frameMs = frames * 1000.0 / rates.frameRate
                val subFrameMs = subFrames * 1000.0 / (rates.frameRate * rates.subFrameRate)
                return (baseMs + frameMs + subFrameMs).toLong()
            }
            return null
        }
        if (colonCount == 1) {
            val parts = v.split(':')
            val m = parts[0].toLongOrNull() ?: return null
            val secPart = parts[1]
            val dot = secPart.indexOf('.')
            if (dot >= 0) {
                val sec = secPart.substring(0, dot).toLongOrNull() ?: return null
                val frac = fractionToMs(secPart.substring(dot + 1)) ?: return null
                return m * 60_000L + sec * 1000L + frac
            }
            val sec = secPart.toLongOrNull() ?: return null
            return m * 60_000L + sec * 1000L
        }
        val match = offsetTimeRe.find(v) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].ifEmpty { "s" }
        return when (unit) {
            "ms" -> number.toLong()
            "s" -> (number * 1000.0).toLong()
            "m" -> (number * 60_000.0).toLong()
            "h" -> (number * 3_600_000.0).toLong()
            "f" -> (number * 1000.0 / rates.frameRate).toLong()
            "t" -> (number * 1000.0 / rates.tickRate).toLong()
            else -> null
        }
    }
}
