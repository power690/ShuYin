package com.xiaowei.player.data

import java.io.File
import java.io.RandomAccessFile

object WavId3Parser {

    class WavTag(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val year: Int = 0,
        val track: Int = 0,
        val lyrics: String? = null,
        val durationMs: Long = 0,
        val apicFileOffset: Long = -1L,
        val apicLength: Int = 0,
        val apicBytes: ByteArray? = null
    ) {
        fun withDuration(ms: Long): WavTag = WavTag(
            title, artist, album, albumArtist, year, track, lyrics, ms,
            apicFileOffset, apicLength, apicBytes
        )
    }

    private fun leInt(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or
            ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)

    private fun beInt(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or
            ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)

    private fun syncSafe(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 0x7F) shl 21) or ((b[p + 1].toInt() and 0x7F) shl 14) or
            ((b[p + 2].toInt() and 0x7F) shl 7) or (b[p + 3].toInt() and 0x7F)

    private fun isId3(b: ByteArray, p: Int): Boolean =
        b[p] == 'I'.code.toByte() && b[p + 1] == 'D'.code.toByte() && b[p + 2] == '3'.code.toByte()

    private fun isRiffWave(b: ByteArray): Boolean =
        b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte() &&
            b[8] == 'W'.code.toByte() && b[9] == 'A'.code.toByte() &&
            b[10] == 'V'.code.toByte() && b[11] == 'E'.code.toByte()

    private fun isAsciiName(b: ByteArray, p: Int, len: Int): Boolean {
        for (i in 0 until len) {
            val c = b[p + i].toInt() and 0xFF
            if (c !in 0x41..0x5A && c !in 0x61..0x7A && c !in 0x30..0x39) return false
        }
        return len > 0
    }

    private fun trimZero(s: String): String = s.trimEnd('\u0000').trim()

    private fun decodeString(enc: Int, b: ByteArray, from: Int, size: Int): String = when (enc) {
        0 -> trimZero(String(b, from, size, Charsets.ISO_8859_1))
        1 -> trimZero(String(b, from, size, Charsets.UTF_16))
        2 -> trimZero(String(b, from, size, Charsets.UTF_16BE))
        else -> trimZero(String(b, from, size, Charsets.UTF_8))
    }

    private fun decodeInfoText(b: ByteArray, from: Int, size: Int): String {
        val s = String(b, from, size, Charsets.UTF_8)
        if (s.contains('\uFFFD')) {
            return try {
                String(b, from, size, charset("GBK")).trimEnd('\u0000')
            } catch (_: Exception) {
                s.trimEnd('\u0000')
            }
        }
        return s.trimEnd('\u0000')
    }

    fun readTag(file: File): WavTag? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val fileLen = raf.length()
                if (fileLen < 44) return null
                val head = ByteArray(12)
                raf.readFully(head)
                if (isId3(head, 0)) {
                    val size = syncSafe(head, 6)
                    if (size > 0 && 10 + size <= fileLen) {
                        raf.seek(0)
                        val tag = ByteArray(10 + size)
                        raf.readFully(tag)
                        return parseId3(tag, 0L, false)
                    }
                    return null
                }
                if (!isRiffWave(head)) return null
                var pos = 12L
                var byteRate = 0
                var dataStart = -1L
                var dataSize = 0L
                val hdr = ByteArray(8)
                var guard = 0
                while (pos + 8 <= fileLen && guard < 256) {
                    guard++
                    raf.seek(pos)
                    raf.readFully(hdr)
                    val id = String(hdr, 0, 4, Charsets.ISO_8859_1)
                    val sz = leInt(hdr, 4).toLong() and 0xFFFFFFFFL
                    if (id == "data") {
                        dataStart = pos + 8
                        dataSize = sz
                        break
                    }
                    if (id == "fmt " && sz >= 16 && pos + 8 + 16 <= fileLen) {
                        val fmt = ByteArray(16)
                        raf.readFully(fmt)
                        byteRate = leInt(fmt, 8)
                    }
                    pos += 8 + sz + (sz and 1L)
                }
                if (dataStart < 0) return null
                val duration = if (byteRate > 0 && dataSize > 0) dataSize * 1000L / byteRate else 0L
                val dataEnd = dataStart + dataSize + (dataSize and 1L)
                var p = dataEnd
                var guard2 = 0
                while (p + 8 <= fileLen && guard2 < 8) {
                    guard2++
                    raf.seek(p)
                    raf.readFully(hdr)
                    val id = String(hdr, 0, 4, Charsets.ISO_8859_1)
                    val sz = leInt(hdr, 4).toLong() and 0xFFFFFFFFL
                    if (id == "ID3 " || id == "id3 ") {
                        if (sz > 10 && p + 8 + sz <= fileLen) {
                            raf.seek(p + 8)
                            val tag = ByteArray(sz.toInt())
                            raf.readFully(tag)
                            val parsed = parseId3(tag, p + 8, false)
                            if (parsed != null) return parsed.withDuration(duration)
                        }
                    } else if (isId3(hdr, 0)) {
                        val size = syncSafe(hdr, 4 + 2)
                        val tagLen = size + 10
                        if (tagLen > 10 && p + tagLen <= fileLen) {
                            raf.seek(p)
                            val tag = ByteArray(tagLen)
                            raf.readFully(tag)
                            val parsed = parseId3(tag, p, false)
                            if (parsed != null) return parsed.withDuration(duration)
                        }
                    } else if (id == "LIST" && sz > 4 && p + 8 + sz <= fileLen) {
                        val body = ByteArray(sz.toInt())
                        raf.seek(p + 8)
                        raf.readFully(body)
                        val parsed = parseListInfo(body)
                        if (parsed != null) return parsed.withDuration(duration)
                    }
                    if (sz <= 0) break
                    p += 8 + sz + (sz and 1L)
                }
                return null
            }
        } catch (_: Exception) {
            return null
        }
    }

    fun readTagRemote(fetch: (Long, Int) -> ByteArray?): WavTag? {
        val head = fetch(0L, 128) ?: return null
        if (head.size < 44) return null
        if (isId3(head, 0)) {
            val size = syncSafe(head, 6)
            if (size <= 0) return null
            val tag = fetch(0L, 10 + size) ?: return null
            return parseId3(tag, 0L, true)
        }
        if (!isRiffWave(head)) return null
        var pos = 12
        var byteRate = 0
        var dataStart = -1L
        var dataSize = 0L
        while (pos + 8 <= head.size) {
            val id = String(head, pos, 4, Charsets.ISO_8859_1)
            val sz = leInt(head, pos + 4).toLong() and 0xFFFFFFFFL
            if (id == "data") {
                dataStart = (pos + 8).toLong()
                dataSize = sz
                break
            }
            if (id == "fmt " && sz >= 16 && pos + 8 + 16 <= head.size) {
                byteRate = leInt(head, pos + 8 + 8)
            }
            if (sz < 0) break
            pos += 8 + sz.toInt()
        }
        if (dataStart < 0) return null
        val duration = if (byteRate > 0 && dataSize > 0) dataSize * 1000L / byteRate else 0L
        val dataEnd = dataStart + dataSize + (dataSize and 1L)
        val probe = fetch(dataEnd, 16) ?: return null
        if (probe.size >= 10 && isId3(probe, 0)) {
            val tagStart: Long
            val tagLen: Int
            if (probe[3] == ' '.code.toByte()) {
                tagStart = dataEnd + 8
                tagLen = leInt(probe, 4)
            } else {
                tagStart = dataEnd
                tagLen = syncSafe(probe, 6) + 10
            }
            if (tagLen <= 10) return null
            val tag = fetch(tagStart, tagLen) ?: return null
            val parsed = parseId3(tag, tagStart, true)
            if (parsed != null) return parsed.withDuration(duration)
        }
        if (probe.size >= 8 &&
            probe[0] == 'L'.code.toByte() && probe[1] == 'I'.code.toByte() &&
            probe[2] == 'S'.code.toByte() && probe[3] == 'T'.code.toByte()
        ) {
            val sz = leInt(probe, 4).toLong() and 0xFFFFFFFFL
            if (sz > 4 && sz < 1024 * 1024) {
                val body = fetch(dataEnd + 8, sz.toInt()) ?: return null
                return parseListInfo(body)?.withDuration(duration)
            }
        }
        return null
    }

    fun readCover(file: File): ByteArray? {
        val tag = readTag(file) ?: return null
        if (tag.apicBytes != null && tag.apicBytes.isNotEmpty()) return tag.apicBytes
        if (tag.apicFileOffset < 0 || tag.apicLength <= 0) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(tag.apicFileOffset)
                val buf = ByteArray(tag.apicLength)
                raf.readFully(buf)
                buf
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseListInfo(body: ByteArray): WavTag? {
        if (body.size < 4) return null
        if (String(body, 0, 4, Charsets.ISO_8859_1) != "INFO") return null
        var p = 4
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var year = 0
        var track = 0
        while (p + 8 <= body.size) {
            val id = String(body, p, 4, Charsets.ISO_8859_1)
            val sz = leInt(body, p + 4)
            if (sz <= 0 || p + 8 + sz > body.size) break
            val raw = decodeInfoText(body, p + 8, sz)
            when (id) {
                "INAM" -> if (title == null) title = raw.takeIf { it.isNotBlank() }
                "IART" -> if (artist == null) artist = raw.takeIf { it.isNotBlank() }
                "IPRD" -> if (album == null) album = raw.takeIf { it.isNotBlank() }
                "ICRD" -> if (year == 0) year = raw.take(4).filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toInt() ?: 0
                "IPRT" -> if (track == 0) track = raw.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toInt() ?: 0
            }
            p += 8 + sz + (sz and 1)
        }
        if (title == null && artist == null && album == null) return null
        return WavTag(title = title, artist = artist, album = album, year = year, track = track)
    }

    private fun parseId3(tag: ByteArray, tagFileOffset: Long, includeCover: Boolean): WavTag? {
        if (tag.size < 10) return null
        if (!isId3(tag, 0)) return null
        val ver = tag[3].toInt() and 0xFF
        if (ver < 2 || ver > 4) return null
        val flags = tag[5].toInt() and 0xFF
        val size = syncSafe(tag, 6)
        if (size <= 0) return null
        val limit = minOf(tag.size, 10 + size)
        var pos = 10
        if (ver >= 3 && (flags and 0x40) != 0 && pos + 4 <= limit) {
            if (ver == 4) {
                val ext = syncSafe(tag, pos)
                pos += ext
            } else {
                val ext = beInt(tag, pos)
                pos += 4 + ext
            }
        }
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var year = 0
        var track = 0
        var lyrics: String? = null
        var apicFileOffset = -1L
        var apicLength = 0
        var apicBytes: ByteArray? = null
        while (pos < limit) {
            if (ver == 2) {
                if (pos + 6 > limit) break
                val id = String(tag, pos, 3, Charsets.ISO_8859_1)
                val fsz = ((tag[pos + 3].toInt() and 0xFF) shl 16) or
                    ((tag[pos + 4].toInt() and 0xFF) shl 8) or
                    (tag[pos + 5].toInt() and 0xFF)
                if (fsz <= 0) break
                if (pos + 6 + fsz > tag.size) break
                val body = tag.copyOfRange(pos + 6, pos + 6 + fsz)
                when (id) {
                    "TT2" -> title = decodeText(body)
                    "TP1" -> artist = decodeText(body)
                    "TAL" -> album = decodeText(body)
                    "TP2" -> albumArtist = decodeText(body)
                    "TYE" -> year = yearOf(body)
                    "TRK" -> track = trackOf(body)
                    "ULT" -> lyrics = decodeUslt(body)
                    "PIC" -> {
                        val r = readApic(body, enc1Only = true)
                        if (r != null) {
                            apicFileOffset = tagFileOffset + pos + 6 + r.first
                            apicLength = r.second
                            if (includeCover) apicBytes = body.copyOfRange(r.first, r.first + r.second)
                        }
                    }
                }
                pos += 6 + fsz
            } else {
                if (pos + 10 > limit) break
                if (!isAsciiName(tag, pos, 4)) break
                val id = String(tag, pos, 4, Charsets.ISO_8859_1)
                val fsz = if (ver == 4) syncSafe(tag, pos + 4) else beInt(tag, pos + 4)
                if (fsz <= 0) break
                if (pos + 10 + fsz > tag.size) break
                val body = tag.copyOfRange(pos + 10, pos + 10 + fsz)
                when (id) {
                    "TIT2" -> title = decodeText(body)
                    "TPE1" -> artist = decodeText(body)
                    "TALB" -> album = decodeText(body)
                    "TPE2" -> albumArtist = decodeText(body)
                    "TDRC" -> year = yearOf(body)
                    "TYER" -> year = yearOf(body)
                    "TRCK" -> track = trackOf(body)
                    "USLT" -> lyrics = decodeUslt(body)
                    "APIC" -> {
                        val r = readApic(body, enc1Only = false)
                        if (r != null) {
                            apicFileOffset = tagFileOffset + pos + 10 + r.first
                            apicLength = r.second
                            if (includeCover) apicBytes = body.copyOfRange(r.first, r.first + r.second)
                        }
                    }
                }
                pos += 10 + fsz
            }
        }
        if (title == null && artist == null && album == null && lyrics == null &&
            apicFileOffset < 0 && albumArtist == null && year == 0 && track == 0
        ) return null
        return WavTag(
            title = title, artist = artist, album = album, albumArtist = albumArtist,
            year = year, track = track, lyrics = lyrics,
            apicFileOffset = apicFileOffset, apicLength = apicLength, apicBytes = apicBytes
        )
    }

    private fun decodeText(body: ByteArray): String? {
        if (body.isEmpty()) return null
        val enc = body[0].toInt() and 0xFF
        val text = decodeString(enc, body, 1, body.size - 1)
        return text.takeIf { it.isNotBlank() }
    }

    private fun yearOf(body: ByteArray): Int {
        if (body.isEmpty()) return 0
        val enc = body[0].toInt() and 0xFF
        val text = decodeString(enc, body, 1, body.size - 1)
        val digits = text.filter { it.isDigit() }.take(4)
        return if (digits.length == 4) digits.toInt() else 0
    }

    private fun trackOf(body: ByteArray): Int {
        if (body.isEmpty()) return 0
        val enc = body[0].toInt() and 0xFF
        val text = decodeString(enc, body, 1, body.size - 1)
        val first = text.substringBefore('/').trim().filter { it.isDigit() }
        return first.takeIf { it.isNotEmpty() }?.toInt() ?: 0
    }

    private fun decodeUslt(body: ByteArray): String? {
        if (body.size < 5) return null
        val enc = body[0].toInt() and 0xFF
        var textStart = -1
        if (enc == 1 || enc == 2) {
            var i = 4
            while (i + 1 < body.size) {
                if (body[i].toInt() == 0 && body[i + 1].toInt() == 0) {
                    textStart = i + 2
                    break
                }
                i += 2
            }
        } else {
            var i = 4
            while (i < body.size) {
                if (body[i].toInt() == 0) {
                    textStart = i + 1
                    break
                }
                i++
            }
        }
        if (textStart < 0 || textStart >= body.size) return null
        val text = decodeString(enc, body, textStart, body.size - textStart)
        return text.takeIf { it.isNotBlank() }
    }

    private fun readApic(body: ByteArray, enc1Only: Boolean): Pair<Int, Int>? {
        if (body.isEmpty()) return null
        val enc = body[0].toInt() and 0xFF
        if (enc1Only) {
            if (body.size < 6) return null
            val picType = body[4]
            val descStart = 5
            var imgStart = -1
            if (enc == 1 || enc == 2) {
                var j = descStart
                while (j + 1 < body.size) {
                    if (body[j].toInt() == 0 && body[j + 1].toInt() == 0) {
                        imgStart = j + 2
                        break
                    }
                    j += 1
                }
            } else {
                var j = descStart
                while (j < body.size) {
                    if (body[j].toInt() == 0) {
                        imgStart = j + 1
                        break
                    }
                    j++
                }
            }
            if (imgStart in 0 until body.size) {
                return Pair(imgStart, body.size - imgStart)
            }
            return null
        }
        if (body.size < 4) return null
        var i = 1
        while (i < body.size && body[i].toInt() != 0) i++
        if (i >= body.size) return null
        val descStart = i + 2
        if (descStart > body.size) return null
        var imgStart = -1
        if (enc == 1 || enc == 2) {
            var j = descStart
            while (j + 1 < body.size) {
                if (body[j].toInt() == 0 && body[j + 1].toInt() == 0) {
                    imgStart = j + 2
                    break
                }
                j += 1
            }
        } else {
            var j = descStart
            while (j < body.size) {
                if (body[j].toInt() == 0) {
                    imgStart = j + 1
                    break
                }
                j++
            }
        }
        if (imgStart in 0 until body.size) {
            return Pair(imgStart, body.size - imgStart)
        }
        return null
    }
}
