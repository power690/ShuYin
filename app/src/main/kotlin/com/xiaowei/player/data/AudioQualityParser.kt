package com.xiaowei.player.data

import java.io.File
import java.io.RandomAccessFile

object AudioQualityParser {

    const val HQ = "HQ"
    const val SQ = "SQ"
    const val HR = "HR"

    private const val HEAD_BYTES = 65536
    private const val MP4_TAIL_BYTES = 1048576
    private const val MP3_SCAN_BYTES = 4096
    private const val LOSSY_HQ_BITRATE = 250_000

    private val LOSSLESS_EXTS = setOf("flac", "wav", "ape")
    private val MP4_EXTS = setOf("m4a", "mp4")
    private val SUPPORTED_EXTS = LOSSLESS_EXTS + MP4_EXTS + setOf("mp3", "ogg", "oga", "opus", "aac")

    private data class AudioInfo(
        val sampleRate: Int,
        val bitDepth: Int,
        val lossless: Boolean,
        val bitrate: Int
    )

    fun parseFile(file: File, durationMs: Long): String? {
        val ext = file.extension.lowercase()
        if (ext !in SUPPORTED_EXTS) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                if (len <= 0) return null
                val headLen = minOf(HEAD_BYTES.toLong(), len).toInt()
                val head = ByteArray(headLen)
                raf.readFully(head)
                var quality = parse(head, ext, len, durationMs)
                if (quality == null && ext in MP4_EXTS && len > headLen) {
                    val tailLen = minOf(MP4_TAIL_BYTES.toLong(), len - headLen).toInt()
                    raf.seek(len - tailLen)
                    val tail = ByteArray(tailLen)
                    raf.readFully(tail)
                    quality = parse(tail, ext, len, durationMs)
                }
                quality
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parse(data: ByteArray, ext: String, sizeBytes: Long, durationMs: Long): String? {
        val info = when (ext) {
            "flac" -> parseFlac(trimId3v2(data))
            "wav" -> parseWav(data)
            "mp3" -> parseMp3(trimId3v2(data))
            "m4a", "mp4" -> parseMp4(data)
            "ogg", "oga" -> parseOgg(data)
            "opus" -> parseOgg(data)
            else -> null
        }
        if (info != null) {
            return if (info.lossless) {
                classifyLossless(info.sampleRate, info.bitDepth)
            } else {
                classifyLossy(info.bitrate, sizeBytes, durationMs)
            }
        }
        if (ext in LOSSLESS_EXTS) return SQ
        if (ext in MP4_EXTS) return null
        return classifyLossy(0, sizeBytes, durationMs)
    }

    private fun classifyLossless(sampleRate: Int, bitDepth: Int): String {
        if (bitDepth > 16 || sampleRate > 48000) return HR
        return SQ
    }

    private fun classifyLossy(headerBitrate: Int, sizeBytes: Long, durationMs: Long): String? {
        val estimated = estimateBitrate(sizeBytes, durationMs)
        val bitrate = estimated ?: if (headerBitrate > 0) headerBitrate else 0
        if (bitrate >= LOSSY_HQ_BITRATE) return HQ
        return null
    }

    private fun estimateBitrate(sizeBytes: Long, durationMs: Long): Int? {
        if (sizeBytes <= 0 || durationMs <= 1000) return null
        val value = sizeBytes * 8000 / durationMs
        if (value <= 0 || value > Int.MAX_VALUE) return null
        return value.toInt()
    }

    private fun trimId3v2(data: ByteArray): ByteArray {
        if (data.size < 10) return data
        if (data[0] != 'I'.code.toByte() || data[1] != 'D'.code.toByte() || data[2] != '3'.code.toByte()) return data
        val tagSize = ((data[6].toLong() and 0x7F) shl 21) or
            ((data[7].toLong() and 0x7F) shl 14) or
            ((data[8].toLong() and 0x7F) shl 7) or
            (data[9].toLong() and 0x7F)
        val start = 10 + tagSize
        if (start >= 1 && start < data.size) return data.copyOfRange(start.toInt(), data.size)
        return data
    }

    private fun parseFlac(body: ByteArray): AudioInfo? {
        if (body.size < 46) return null
        if (fourccAt(body, 0) != "fLaC") return null
        var p = 4
        while (p + 4 <= body.size) {
            val header = body[p].toInt() and 0xFF
            val blockType = header and 0x7F
            val isLast = (header and 0x80) != 0
            val blockLen = ((body[p + 1].toLong() and 0xFF) shl 16) or
                ((body[p + 2].toLong() and 0xFF) shl 8) or
                (body[p + 3].toLong() and 0xFF)
            if (blockType == 0 && blockLen >= 34 && p + 4 + 34 <= body.size) {
                val o = p + 4
                var v = 0L
                for (i in 10..17) {
                    v = (v shl 8) or (body[o + i].toLong() and 0xFF)
                }
                val sampleRate = ((v shr 44) and 0xFFFFFL).toInt()
                val bitDepth = ((v shr 36) and 0x1FL).toInt() + 1
                if (sampleRate in 8000..768000 && bitDepth in 4..32) {
                    return AudioInfo(sampleRate, bitDepth, true, 0)
                }
                return null
            }
            if (isLast) return null
            p += 4 + blockLen.toInt()
        }
        return null
    }

    private fun parseWav(head: ByteArray): AudioInfo? {
        if (head.size < 12) return null
        if (fourccAt(head, 0) != "RIFF" || fourccAt(head, 8) != "WAVE") return null
        var p = 12
        while (p + 8 <= head.size) {
            val id = fourccAt(head, p)
            val size = readU32LE(head, p + 4)
            if (size < 0 || size > Int.MAX_VALUE) return null
            val chunkSize = size.toInt()
            if (id == "fmt " && chunkSize >= 16 && p + 8 + 16 <= head.size) {
                var format = readU16LE(head, p + 8)
                val sampleRate = readU32LE(head, p + 12).toInt()
                val bitDepth = readU16LE(head, p + 22)
                if (format == 0xFFFE && chunkSize >= 40 && p + 8 + 40 <= head.size) {
                    format = readU16LE(head, p + 32)
                }
                val lossless = format == 1 || format == 3
                if (sampleRate in 8000..384000 && bitDepth in 4..64) {
                    return AudioInfo(sampleRate, bitDepth, lossless, 0)
                }
                return null
            }
            p += 8 + chunkSize + (chunkSize and 1)
        }
        return null
    }

    private fun parseMp3(body: ByteArray): AudioInfo? {
        if (body.size < 4) return null
        val limit = minOf(body.size, MP3_SCAN_BYTES)
        var p = 0
        while (p + 4 <= limit) {
            if ((body[p].toInt() and 0xFF) == 0xFF && (body[p + 1].toInt() and 0xE0) == 0xE0) {
                val versionBits = (body[p + 1].toInt() shr 3) and 0x3
                val layerBits = (body[p + 1].toInt() shr 1) and 0x3
                val bitrateIndex = (body[p + 2].toInt() shr 4) and 0xF
                val srIndex = (body[p + 2].toInt() shr 2) and 0x3
                if (versionBits != 1 && layerBits == 1 && bitrateIndex in 1..14 && srIndex != 3) {
                    val bitrate = if (versionBits == 3) {
                        MP3_BITRATES_V1L3[bitrateIndex]
                    } else {
                        MP3_BITRATES_V2L3[bitrateIndex]
                    }
                    val sampleRate = MP3_SAMPLE_RATES[sampleRateTableIndex(versionBits)][srIndex]
                    if (bitrate > 0 && sampleRate > 0) {
                        val coeff = if (versionBits == 3) 144 else 72
                        val padding = (body[p + 2].toInt() shr 1) and 0x1
                        val frameLen = coeff * bitrate * 1000 / sampleRate + padding
                        val next = p + frameLen
                        if (next + 1 < body.size &&
                            (body[next].toInt() and 0xFF) == 0xFF &&
                            (body[next + 1].toInt() and 0xE0) == 0xE0
                        ) {
                            return AudioInfo(sampleRate, 0, false, bitrate)
                        }
                    }
                }
            }
            p++
        }
        return null
    }

    private fun sampleRateTableIndex(versionBits: Int): Int = when (versionBits) {
        3 -> 0
        2 -> 1
        else -> 2
    }

    private fun parseMp4(data: ByteArray): AudioInfo? {
        var search = 0
        while (true) {
            val pos = indexOfFourcc(data, search, "stsd") ?: return null
            var p = pos + 8
            if (p + 4 > data.size) {
                search = pos + 4
                continue
            }
            val entryCount = readU32BE(data, p).toInt()
            p += 4
            if (entryCount <= 0 || entryCount > 8) {
                search = pos + 4
                continue
            }
            var entry = 0
            while (entry < entryCount && p + 8 <= data.size) {
                val entrySize = readU32BE(data, p).toInt()
                val fourcc = fourccAt(data, p + 4)
                if (entrySize >= 16 && p + entrySize <= data.size) {
                    when (fourcc) {
                        "alac" -> {
                            val info = parseAlac(data, p + 8, p + entrySize)
                            if (info != null) return info
                        }
                        "mp4a" -> return AudioInfo(0, 0, false, 0)
                    }
                    p += entrySize
                } else {
                    break
                }
                entry++
            }
            search = pos + 4
        }
    }

    private fun parseAlac(data: ByteArray, payloadStart: Int, payloadEnd: Int): AudioInfo? {
        if (payloadEnd - payloadStart < 28) return null
        var bitDepth = readU16BE(data, payloadStart + 18)
        var sampleRate = (readU32BE(data, payloadStart + 24) shr 16).toInt()
        if (payloadEnd - payloadStart >= 54) {
            val bd = data[payloadStart + 33].toInt() and 0xFF
            if (bd in 8..64) bitDepth = bd
            val sr = readU32BE(data, payloadStart + 50).toInt()
            if (sr in 8000..384000) sampleRate = sr
        }
        if (bitDepth !in 8..64 || sampleRate !in 8000..384000) return null
        return AudioInfo(sampleRate, bitDepth, true, 0)
    }

    private fun parseOgg(head: ByteArray): AudioInfo? {
        if (head.size < 64) return null
        if (fourccAt(head, 0) != "OggS") return null
        val segCount = head[26].toInt() and 0xFF
        if (segCount <= 0 || 27 + segCount > head.size) return null
        val payloadStart = 27 + segCount
        if (payloadStart + 8 > head.size) return null
        if (fourccAt(head, payloadStart) == "Opus") {
            if (payloadStart + 16 <= head.size) {
                return AudioInfo(48000, 0, false, 0)
            }
            return null
        }
        if (payloadStart + 7 <= head.size &&
            (head[payloadStart].toInt() and 0xFF) == 0x01 &&
            fourccAt(head, payloadStart + 1) == "vorbis"
        ) {
            val q = payloadStart + 7
            if (q + 21 <= head.size) {
                val sampleRate = readU32LE(head, q + 5).toInt()
                val nominal = readU32LE(head, q + 13).toInt()
                if (sampleRate in 8000..192000) {
                    return AudioInfo(sampleRate, 0, false, if (nominal > 0) nominal else 0)
                }
            }
        }
        return null
    }

    private fun fourccAt(data: ByteArray, pos: Int): String {
        if (pos < 0 || pos + 4 > data.size) return ""
        return String(data, pos, 4, Charsets.ISO_8859_1)
    }

    private fun indexOfFourcc(data: ByteArray, from: Int, fourcc: String): Int? {
        if (fourcc.length != 4) return null
        val b0 = fourcc[0].code
        val b1 = fourcc[1].code
        val b2 = fourcc[2].code
        val b3 = fourcc[3].code
        var i = from
        while (i + 4 <= data.size) {
            if ((data[i].toInt() and 0xFF) == b0 &&
                (data[i + 1].toInt() and 0xFF) == b1 &&
                (data[i + 2].toInt() and 0xFF) == b2 &&
                (data[i + 3].toInt() and 0xFF) == b3
            ) return i
            i++
        }
        return null
    }

    private fun readU32BE(data: ByteArray, pos: Int): Long {
        if (pos < 0 || pos + 4 > data.size) return -1L
        return ((data[pos].toLong() and 0xFF) shl 24) or
            ((data[pos + 1].toLong() and 0xFF) shl 16) or
            ((data[pos + 2].toLong() and 0xFF) shl 8) or
            (data[pos + 3].toLong() and 0xFF)
    }

    private fun readU32LE(data: ByteArray, pos: Int): Long {
        if (pos < 0 || pos + 4 > data.size) return -1L
        return (data[pos].toLong() and 0xFF) or
            ((data[pos + 1].toLong() and 0xFF) shl 8) or
            ((data[pos + 2].toLong() and 0xFF) shl 16) or
            ((data[pos + 3].toLong() and 0xFF) shl 24)
    }

    private fun readU16BE(data: ByteArray, pos: Int): Int {
        if (pos < 0 || pos + 2 > data.size) return -1
        return ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
    }

    private fun readU16LE(data: ByteArray, pos: Int): Int {
        if (pos < 0 || pos + 2 > data.size) return -1
        return (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)
    }

    private val MP3_BITRATES_V1L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val MP3_BITRATES_V2L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    private val MP3_SAMPLE_RATES = arrayOf(
        intArrayOf(44100, 48000, 32000),
        intArrayOf(22050, 24000, 16000),
        intArrayOf(11025, 12000, 8000)
    )
}
