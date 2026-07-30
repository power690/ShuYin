package com.xiaowei.player.lyric

import java.io.File
import java.io.RandomAccessFile


object LyricWriter {

    enum class WriteResult { SUCCESS, UNSUPPORTED_FORMAT, WRITE_FAILED, NO_LYRIC }

    
    data class LyricsContent(
        val lrc: String,
        val songHash: String? = null
    )

    
    fun readLyrics(file: File): String? {
        return when (file.extension.lowercase()) {
            "flac" -> FlacWriter.readLyrics(file)
            "mp3" -> Id3Writer.readLyrics(file)
            else -> null
        }
    }

    
    fun readLyricRid(file: File): String? {
        return when (file.extension.lowercase()) {
            "flac" -> FlacWriter.readLyricRid(file)
            "mp3" -> Id3Writer.readLyricRid(file)
            else -> null
        }
    }

    
    fun readLyricSource(file: File): String? {
        return when (file.extension.lowercase()) {
            "flac" -> FlacWriter.readLyricSource(file)
            "mp3" -> Id3Writer.readLyricSource(file)
            else -> null
        }
    }

    
    fun writeLyrics(file: File, content: LyricsContent): WriteResult {
        if (content.lrc.isBlank()) return WriteResult.NO_LYRIC
        return try {
            
            val bak = File(file.parentFile, file.name + ".bak")
            if (!bak.exists()) {
                file.copyTo(bak, overwrite = false)
            }

            when (file.extension.lowercase()) {
                "flac" -> FlacWriter.writeLyrics(file, content)
                "mp3" -> Id3Writer.writeLyrics(file, content)
                else -> return WriteResult.UNSUPPORTED_FORMAT
            }
            WriteResult.SUCCESS
        } catch (e: Exception) {
            WriteResult.WRITE_FAILED
        }
    }

    
    
    
    private object FlacWriter {
        private const val BLOCK_VORBIS_COMMENT = 4

        fun readLyrics(file: File): String? {
            try {
                RandomAccessFile(file, "r").use { raf ->
                    val magic = ByteArray(4); raf.readFully(magic)
                    if (String(magic) != "fLaC") return null

                    var lastBlock = false
                    while (!lastBlock) {
                        val header = raf.readByte().toInt() and 0xFF
                        lastBlock = (header and 0x80) != 0
                        val type = header and 0x7F
                        val len = read3BytesBE(raf)
                        if (type == BLOCK_VORBIS_COMMENT) {
                            val data = ByteArray(len); raf.readFully(data)
                            return parseVorbisCommentLyrics(data)
                        } else {
                            raf.seek(raf.filePointer + len)
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }

        fun readLyricRid(file: File): String? {
            try {
                RandomAccessFile(file, "r").use { raf ->
                    val magic = ByteArray(4); raf.readFully(magic)
                    if (String(magic) != "fLaC") return null
                    var lastBlock = false
                    while (!lastBlock) {
                        val header = raf.readByte().toInt() and 0xFF
                        lastBlock = (header and 0x80) != 0
                        val type = header and 0x7F
                        val len = read3BytesBE(raf)
                        if (type == BLOCK_VORBIS_COMMENT) {
                            val data = ByteArray(len); raf.readFully(data)
                            return parseVorbisCommentField(data, "LYRIC_RID")
                        } else {
                            raf.seek(raf.filePointer + len)
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }

        fun readLyricSource(file: File): String? {
            try {
                RandomAccessFile(file, "r").use { raf ->
                    val magic = ByteArray(4); raf.readFully(magic)
                    if (String(magic) != "fLaC") return null
                    var lastBlock = false
                    while (!lastBlock) {
                        val header = raf.readByte().toInt() and 0xFF
                        lastBlock = (header and 0x80) != 0
                        val type = header and 0x7F
                        val len = read3BytesBE(raf)
                        if (type == BLOCK_VORBIS_COMMENT) {
                            val data = ByteArray(len); raf.readFully(data)
                            return parseVorbisCommentField(data, "LYRIC_SOURCE")
                        } else {
                            raf.seek(raf.filePointer + len)
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }

        fun writeLyrics(file: File, content: LyricsContent) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            val raf = RandomAccessFile(file, "r")
            try {
                val magic = ByteArray(4); raf.readFully(magic)
                require(String(magic) == "fLaC") { "不是 FLAC 文件" }

                var firstVorbisPos = -1L
                var firstVorbisLen = 0
                var audioStart = -1L

                while (true) {
                    val blockHeaderPos = raf.filePointer
                    val header = raf.readByte().toInt() and 0xFF
                    val lastBlock = (header and 0x80) != 0
                    val type = header and 0x7F
                    val len = read3BytesBE(raf)
                    if (type == BLOCK_VORBIS_COMMENT && firstVorbisPos < 0) {
                        firstVorbisPos = blockHeaderPos
                        firstVorbisLen = len
                    }
                    if (lastBlock) {
                        audioStart = raf.filePointer + len
                        break
                    }
                    raf.seek(raf.filePointer + len)
                }
                require(audioStart > 0) { "找不到音频数据起点" }

                val oldComments = if (firstVorbisPos >= 0) {
                    raf.seek(firstVorbisPos + 4)
                    val vcData = ByteArray(firstVorbisLen); raf.readFully(vcData)
                    parseAllVorbisComments(vcData, 0, firstVorbisLen)
                } else {
                    VorbisComments("xiaowei-player", emptyList())
                }

                val newVcData = buildVorbisCommentPreserving(oldComments, content)

                java.io.FileOutputStream(tmp).use { fos ->
                    fos.write(magic)

                    raf.seek(4)
                    while (true) {
                        val blockHeaderPos = raf.filePointer
                        val header = raf.readByte().toInt() and 0xFF
                        val lastBlock = (header and 0x80) != 0
                        val type = header and 0x7F
                        val len = read3BytesBE(raf)

                        if (blockHeaderPos != firstVorbisPos) {
                            val h = if (lastBlock) (type and 0x7F) else header
                            fos.write(h)
                            write3BytesBE(fos, len)
                            copyBytes(raf, fos, len)
                        } else {
                            raf.seek(raf.filePointer + len)
                        }

                        if (lastBlock) break
                    }

                    fos.write(0x80 or BLOCK_VORBIS_COMMENT)
                    write3BytesBE(fos, newVcData.size)
                    fos.write(newVcData)

                    raf.seek(audioStart)
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = raf.read(buf)
                        if (n < 0) break
                        fos.write(buf, 0, n)
                    }
                }

                raf.close()
                if (!file.delete()) throw java.io.IOException("无法删除原文件 ${file.absolutePath}")
                if (!tmp.renameTo(file)) throw java.io.IOException("无法重命名临时文件到 ${file.absolutePath}")
            } finally {
                try { raf.close() } catch (_: Exception) {}
                if (tmp.exists()) tmp.delete()
            }
        }

        private fun copyBytes(raf: RandomAccessFile, out: java.io.OutputStream, len: Int) {
            val buf = ByteArray(minOf(len, 64 * 1024))
            var remaining = len
            while (remaining > 0) {
                val n = minOf(remaining, buf.size)
                raf.readFully(buf, 0, n)
                out.write(buf, 0, n)
                remaining -= n
            }
        }

        private data class VorbisComments(
            val vendor: String,
            val comments: List<String>
        )

        private fun parseAllVorbisComments(data: ByteArray, off: Int, len: Int): VorbisComments {
            try {
                var p = off
                val end = off + len
                val vendorLen = readLE32(data, p); p += 4
                val vendor = String(data, p, vendorLen, Charsets.UTF_8)
                p += vendorLen
                val count = readLE32(data, p); p += 4
                val list = mutableListOf<String>()
                for (i in 0 until count) {
                    if (p + 4 > end) break
                    val clen = readLE32(data, p); p += 4
                    if (p + clen > end) break
                    val c = String(data, p, clen, Charsets.UTF_8)
                    list.add(c)
                    p += clen
                }
                return VorbisComments(vendor, list)
            } catch (_: Exception) {
                return VorbisComments("xiaowei-player", emptyList())
            }
        }

        private fun parseVorbisCommentLyrics(data: ByteArray): String? {
            try {
                var p = 0
                val vendorLen = readLE32(data, p); p += 4
                p += vendorLen
                val count = readLE32(data, p); p += 4
                var lyrics: String? = null
                for (i in 0 until count) {
                    val clen = readLE32(data, p); p += 4
                    val c = String(data, p, clen, Charsets.UTF_8)
                    p += clen
                    val eq = c.indexOf('=')
                    if (eq < 0) continue
                    val key = c.substring(0, eq).uppercase()
                    val value = c.substring(eq + 1)
                    if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "LYRIC") {
                        if (lyrics == null) lyrics = value
                    }
                }
                return lyrics
            } catch (_: Exception) { return null }
        }

        private fun parseVorbisCommentField(data: ByteArray, fieldName: String): String? {
            try {
                var p = 0
                val vendorLen = readLE32(data, p); p += 4
                p += vendorLen
                val count = readLE32(data, p); p += 4
                val target = fieldName.uppercase()
                for (i in 0 until count) {
                    val clen = readLE32(data, p); p += 4
                    val c = String(data, p, clen, Charsets.UTF_8)
                    p += clen
                    val eq = c.indexOf('=')
                    if (eq < 0) continue
                    val key = c.substring(0, eq).uppercase()
                    if (key == target) return c.substring(eq + 1)
                }
            } catch (_: Exception) {}
            return null
        }

        private fun buildVorbisCommentPreserving(
            old: VorbisComments,
            content: LyricsContent
        ): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val vendorBytes = old.vendor.toByteArray(Charsets.UTF_8)
            writeLE32(out, vendorBytes.size)
            out.write(vendorBytes)

            val lyricFields = mapOf(
                "LYRICS" to content.lrc,
                "UNSYNCEDLYRICS" to content.lrc
            )

            val preserved = mutableListOf<String>()
            for (c in old.comments) {
                val eq = c.indexOf('=')
                if (eq < 0) {
                    preserved.add(c)
                    continue
                }
                val key = c.substring(0, eq).uppercase()
                if (key in lyricFields) continue
                if (key == "LYRIC_SOURCE" || key == "LYRIC_RID" || key == "KLYRIC") continue
                preserved.add(c)
            }

            val finalComments = preserved + buildList {
                add("LYRICS=${content.lrc}")
                add("UNSYNCEDLYRICS=${content.lrc}")
                add("LYRIC_SOURCE=kugou-krc")
                if (!content.songHash.isNullOrBlank()) add("LYRIC_RID=${content.songHash}")
            }

            writeLE32(out, finalComments.size)
            for (c in finalComments) {
                val cb = c.toByteArray(Charsets.UTF_8)
                writeLE32(out, cb.size)
                out.write(cb)
            }
            return out.toByteArray()
        }

        private fun read3BytesBE(raf: RandomAccessFile): Int {
            val b = ByteArray(3); raf.readFully(b)
            return ((b[0].toInt() and 0xFF) shl 16) or ((b[1].toInt() and 0xFF) shl 8) or (b[2].toInt() and 0xFF)
        }
        private fun write3BytesBE(out: java.io.OutputStream, v: Int) {
            out.write((v shr 16) and 0xFF)
            out.write((v shr 8) and 0xFF)
            out.write(v and 0xFF)
        }
        private fun readLE32(b: ByteArray, off: Int): Int {
            return (b[off].toInt() and 0xFF) or
                    ((b[off + 1].toInt() and 0xFF) shl 8) or
                    ((b[off + 2].toInt() and 0xFF) shl 16) or
                    ((b[off + 3].toInt() and 0xFF) shl 24)
        }
        private fun writeLE32(out: java.io.ByteArrayOutputStream, v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF)
            out.write((v shr 24) and 0xFF)
        }
    }

    
    private object Id3Writer {
        fun readLyrics(file: File): String? {
            try {
                val bytes = file.readBytes()
                if (bytes.size < 10) return null
                if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) return null
                val majorVer = bytes[3].toInt() and 0xFF
                val size = synchsafeToInt(bytes, 6)
                val tagEnd = 10 + size
                if (tagEnd > bytes.size) return null
                var p = 10
                while (p < tagEnd) {
                    if (p + 10 > tagEnd) break
                    val frameId = String(bytes, p, 4, Charsets.US_ASCII)
                    if (frameId[0] == '\u0000') break
                    val frameSize = if (majorVer >= 4) synchsafeToInt(bytes, p + 4) else intBE(bytes, p + 4)
                    if (frameSize <= 0 || p + 10 + frameSize > tagEnd) break
                    if (frameId == "USLT") {
                        var off = p + 10
                        val encoding = bytes[off].toInt() and 0xFF; off++
                        off += 3
                        if (encoding == 0 || encoding == 3) {
                            val end = findZero(bytes, off, p + 10 + frameSize, if (encoding==3) 2 else 1)
                            val cs = if (encoding == 3) Charsets.UTF_8 else Charsets.ISO_8859_1
                            return String(bytes, off, end - off, cs)
                        } else if (encoding == 1 || encoding == 2) {
                            val end = findZero(bytes, off, p + 10 + frameSize, 2)
                            return String(bytes, off, end - off, Charsets.UTF_16LE)
                        }
                    }
                    p += 10 + frameSize
                }
            } catch (_: Exception) {}
            return null
        }

        fun writeLyrics(file: File, content: LyricsContent) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            val raf = RandomAccessFile(file, "r")
            try {
                var id3End = 0
                var majorVer = 4
                if (raf.length() >= 10) {
                    val head = ByteArray(10); raf.readFully(head)
                    if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
                        majorVer = head[3].toInt() and 0xFF
                        val size = synchsafeToInt(head, 6)
                        id3End = 10 + size
                    }
                }

                val id3Bytes = if (id3End > 0) {
                    raf.seek(0)
                    val b = ByteArray(id3End); raf.readFully(b)
                    b
                } else ByteArray(0)

                val preservedFrames = mutableListOf<ByteArray>()
                if (id3End > 0) {
                    var p = 10
                    while (p + 10 <= id3End) {
                        val frameId = String(id3Bytes, p, 4, Charsets.US_ASCII)
                        if (frameId[0] == '\u0000') break
                        val frameSize = if (majorVer >= 4) synchsafeToInt(id3Bytes, p + 4) else intBE(id3Bytes, p + 4)
                        if (frameSize <= 0 || p + 10 + frameSize > id3End) break
                        if (frameId == "USLT") { p += 10 + frameSize; continue }
                        if (frameId == "TXXX") {
                            val desc = readTxxxDescription(id3Bytes, p + 10, frameSize)
                            if (desc.equals("LYRIC_RID", ignoreCase = true) ||
                                desc.equals("LYRIC_SOURCE", ignoreCase = true)) {
                                p += 10 + frameSize; continue
                            }
                        }
                        preservedFrames.add(id3Bytes.copyOfRange(p, p + 10 + frameSize))
                        p += 10 + frameSize
                    }
                }

                val newFrames = mutableListOf<ByteArray>()
                newFrames.add(buildUsltFrame(content.lrc))
                newFrames.add(buildTxxxFrame("LYRIC_SOURCE", "kugou-krc"))
                if (!content.songHash.isNullOrBlank()) {
                    newFrames.add(buildTxxxFrame("LYRIC_RID", content.songHash))
                }
                val tagBytes = buildId3v24(preservedFrames + newFrames)

                java.io.FileOutputStream(tmp).use { fos ->
                    fos.write(tagBytes)
                    raf.seek(id3End.toLong())
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = raf.read(buf)
                        if (n < 0) break
                        fos.write(buf, 0, n)
                    }
                }

                raf.close()
                if (!file.delete()) throw java.io.IOException("无法删除原文件 ${file.absolutePath}")
                if (!tmp.renameTo(file)) throw java.io.IOException("无法重命名临时文件到 ${file.absolutePath}")
            } finally {
                try { raf.close() } catch (_: Exception) {}
                if (tmp.exists()) tmp.delete()
            }
        }

        fun readLyricRid(file: File): String? = readTxxxValue(file, "LYRIC_RID")
        fun readLyricSource(file: File): String? = readTxxxValue(file, "LYRIC_SOURCE")

        private fun readTxxxValue(file: File, targetDesc: String): String? {
            try {
                val bytes = file.readBytes()
                if (bytes.size < 10) return null
                if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) return null
                val majorVer = bytes[3].toInt() and 0xFF
                val size = synchsafeToInt(bytes, 6)
                val tagEnd = 10 + size
                if (tagEnd > bytes.size) return null
                var p = 10
                while (p + 10 <= tagEnd) {
                    val frameId = String(bytes, p, 4, Charsets.US_ASCII)
                    if (frameId[0] == '\u0000') break
                    val frameSize = if (majorVer >= 4) synchsafeToInt(bytes, p + 4) else intBE(bytes, p + 4)
                    if (frameSize <= 0 || p + 10 + frameSize > tagEnd) break
                    if (frameId == "TXXX") {
                        val (desc, value) = readTxxx(bytes, p + 10, frameSize)
                        if (desc.equals(targetDesc, ignoreCase = true)) return value
                    }
                    p += 10 + frameSize
                }
            } catch (_: Exception) {}
            return null
        }

        private fun readTxxxDescription(b: ByteArray, off: Int, size: Int): String? {
            return try {
                val (desc, _) = readTxxx(b, off, size)
                desc
            } catch (_: Exception) { null }
        }

        private fun readTxxx(b: ByteArray, off: Int, size: Int): Pair<String, String> {
            val end = off + size
            val encoding = b[off].toInt() and 0xFF
            var p = off + 1
            val cs = when (encoding) {
                0 -> Charsets.ISO_8859_1 to 1
                1 -> Charsets.UTF_16LE to 2
                2 -> Charsets.UTF_16BE to 2
                3 -> Charsets.UTF_8 to 1
                else -> Charsets.UTF_8 to 1
            }
            val (charset, zeroLen) = cs
            val descEnd = findZero(b, p, end, zeroLen)
            val desc = String(b, p, descEnd - p, charset)
            p = descEnd + zeroLen
            var valueEnd = end
            while (valueEnd > p && b[valueEnd - 1] == 0.toByte()) valueEnd--
            val value = String(b, p, valueEnd - p, charset)
            return desc to value
        }

        private fun buildTxxxFrame(description: String, value: String): ByteArray {
            val payload = java.io.ByteArrayOutputStream()
            payload.write(3)
            payload.write(description.toByteArray(Charsets.UTF_8))
            payload.write(0)
            payload.write(value.toByteArray(Charsets.UTF_8))
            val payloadBytes = payload.toByteArray()
            val out = java.io.ByteArrayOutputStream()
            out.write("TXXX".toByteArray(Charsets.US_ASCII))
            writeSynchsafe(out, payloadBytes.size)
            out.write(0); out.write(0)
            out.write(payloadBytes)
            return out.toByteArray()
        }

        private fun buildUsltFrame(lyrics: String): ByteArray {
            val payload = java.io.ByteArrayOutputStream()
            payload.write(3)
            payload.write("eng".toByteArray(Charsets.US_ASCII))
            payload.write(0)
            payload.write(lyrics.toByteArray(Charsets.UTF_8))
            payload.write(0)
            val payloadBytes = payload.toByteArray()
            val out = java.io.ByteArrayOutputStream()
            out.write("USLT".toByteArray(Charsets.US_ASCII))
            writeSynchsafe(out, payloadBytes.size)
            out.write(0); out.write(0)
            out.write(payloadBytes)
            return out.toByteArray()
        }

        private fun buildId3v24(frames: List<ByteArray>): ByteArray {
            val framesData = java.io.ByteArrayOutputStream()
            for (f in frames) framesData.write(f)
            val out = java.io.ByteArrayOutputStream()
            out.write("ID3".toByteArray(Charsets.US_ASCII))
            out.write(4); out.write(0)
            out.write(0)
            writeSynchsafe(out, framesData.size())
            out.write(framesData.toByteArray())
            return out.toByteArray()
        }

        private fun synchsafeToInt(b: ByteArray, off: Int): Int {
            return ((b[off].toInt() and 0x7F) shl 21) or
                    ((b[off + 1].toInt() and 0x7F) shl 14) or
                    ((b[off + 2].toInt() and 0x7F) shl 7) or
                    (b[off + 3].toInt() and 0x7F)
        }
        private fun intBE(b: ByteArray, off: Int): Int {
            return ((b[off].toInt() and 0xFF) shl 24) or
                    ((b[off + 1].toInt() and 0xFF) shl 16) or
                    ((b[off + 2].toInt() and 0xFF) shl 8) or
                    (b[off + 3].toInt() and 0xFF)
        }
        private fun writeSynchsafe(out: java.io.ByteArrayOutputStream, v: Int) {
            out.write((v shr 21) and 0x7F)
            out.write((v shr 14) and 0x7F)
            out.write((v shr 7) and 0x7F)
            out.write(v and 0x7F)
        }
        private fun findZero(b: ByteArray, from: Int, to: Int, zeroCount: Int): Int {
            var count = 0
            for (i in from until to) {
                if (b[i] == 0.toByte()) {
                    count++
                    if (count >= zeroCount) return i - zeroCount + 1
                } else {
                    count = 0
                }
            }
            return to
        }
    }
}
