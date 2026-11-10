package com.xiaowei.player.data

import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

object ApeMetadataParser {

    data class ApeTag(
        val durationMs: Long,
        val title: String?,
        val artist: String?,
        val album: String?,
        val albumArtist: String?,
        val year: Int,
        val track: Int,
        val lyrics: String?,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val coverBytes: ByteArray?
    )

    data class HeaderInfo(
        val durationMs: Long,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int
    )

    private const val PROBE_BYTES = 160
    private const val ID3V1_BYTES = 128
    private const val APE_FOOTER_BYTES = 32
    private const val MAX_TAG_BYTES = 8 * 1024 * 1024
    private const val MAX_HEAD_SCAN = 4096
    private const val CACHE_LIMIT = 128

    private class CacheEntry(val sizeBytes: Long, val mtime: Long, val tag: ApeTag?)

    private val cache = object : android.util.LruCache<String, CacheEntry>(CACHE_LIMIT) {}

    fun parse(file: File): ApeTag? {
        if (!file.exists() || !file.canRead()) return null
        val size = file.length()
        val mtime = file.lastModified()
        val cached = cache.get(file.absolutePath)
        if (cached != null && cached.sizeBytes == size && cached.mtime == mtime) {
            return cached.tag
        }
        val tag = try {
            parseInternal(file)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
        cache.put(file.absolutePath, CacheEntry(size, mtime, tag))
        return tag
    }

    fun readCover(file: File): ByteArray? {
        return parse(file)?.coverBytes
    }

    fun parseHeader(file: File): HeaderInfo? {
        val tag = parse(file) ?: return null
        return HeaderInfo(tag.durationMs, tag.sampleRate, tag.channels, tag.bitsPerSample)
    }

    private fun parseInternal(file: File): ApeTag? {
        RandomAccessFile(file, "r").use { raf ->
            val fileLen = raf.length()
            if (fileLen < 64) return null
            val head = readHead(raf)
            val magicOffset = findMagicOffset(head) ?: return null
            val header = parseHeaderAt(head, magicOffset) ?: return null
            val probeLen = minOf(PROBE_BYTES.toLong(), fileLen).toInt()
            raf.seek(fileLen - probeLen)
            val probe = ByteArray(probeLen)
            raf.readFully(probe)
            val tagData = readApeTagRegion(raf, fileLen, probe)
            val id3v1 = parseId3v1(probe)
            return buildTag(header, tagData, id3v1)
        }
    }

    private fun readHead(raf: RandomAccessFile): ByteArray {
        var skip = 0L
        while (true) {
            raf.seek(skip)
            val probe = ByteArray(10)
            if (raf.read(probe) < 10) break
            if (probe[0] == 'I'.code.toByte() && probe[1] == 'D'.code.toByte() && probe[2] == '3'.code.toByte()) {
                val dataLen = ((probe[6].toLong() and 0x7F) shl 21) or
                    ((probe[7].toLong() and 0x7F) shl 14) or
                    ((probe[8].toLong() and 0x7F) shl 7) or
                    (probe[9].toLong() and 0x7F)
                val hasFooter = (probe[5].toInt() and 0x10) != 0
                skip += 10 + dataLen + (if (hasFooter) 10 else 0)
                if (skip > MAX_HEAD_SCAN * 64) break
            } else {
                break
            }
        }
        val fileLen = raf.length()
        if (skip >= fileLen) {
            raf.seek(0)
            val buf = ByteArray(minOf(MAX_HEAD_SCAN.toLong(), fileLen).toInt())
            raf.readFully(buf)
            return buf
        }
        val windowLen = minOf(MAX_HEAD_SCAN.toLong(), fileLen - skip).toInt()
        raf.seek(skip)
        val buf = ByteArray(windowLen)
        raf.readFully(buf)
        return buf
    }

    private fun findMagicOffset(head: ByteArray): Int? {
        val limit = head.size - 6
        var i = 0
        while (i <= limit) {
            if (head[i] == 'M'.code.toByte() && head[i + 1] == 'A'.code.toByte() &&
                head[i + 2] == 'C'.code.toByte() &&
                (head[i + 3] == ' '.code.toByte() || head[i + 3] == 'F'.code.toByte())
            ) {
                val version = (head[i + 4].toInt() and 0xFF) or ((head[i + 5].toInt() and 0xFF) shl 8)
                if (version in 1000..9999) return i
            }
            i++
        }
        return null
    }

    private fun parseHeaderAt(head: ByteArray, off: Int): HeaderInfo? {
        val version = readU16(head, off + 4) ?: return null
        if (version >= 3980) {
            val descriptorBytes = readU32(head, off + 8) ?: return null
            if (descriptorBytes < 52 || descriptorBytes > 4096) return null
            val h = (off + descriptorBytes).toInt()
            if (h + 24 > head.size) return null
            val blocksPerFrame = readU32(head, h + 4) ?: return null
            val finalFrameBlocks = readU32(head, h + 8) ?: return null
            val totalFrames = readU32(head, h + 12) ?: return null
            val bits = readU16(head, h + 16) ?: return null
            val channels = readU16(head, h + 18) ?: return null
            val sampleRate = readU32(head, h + 20)?.toInt() ?: return null
            return buildHeader(version, blocksPerFrame, finalFrameBlocks, totalFrames, channels, sampleRate, bits, 0)
        }
        if (off + 32 > head.size) return null
        val compressionLevel = readU16(head, off + 6) ?: return null
        val formatFlags = readU16(head, off + 8) ?: return null
        val channels = readU16(head, off + 10) ?: return null
        val sampleRate = readU32(head, off + 12)?.toInt() ?: return null
        val totalFrames = readU32(head, off + 24) ?: return null
        val finalFrameBlocks = readU32(head, off + 28) ?: return null
        val bits = when {
            (formatFlags and 1) != 0 -> 8
            (formatFlags and 8) != 0 -> 24
            else -> 16
        }
        val blocksPerFrame = when {
            version >= 3950 -> 73728L * 4
            version >= 3900 || (version >= 3800 && compressionLevel == 4000) -> 73728L
            else -> 9216L
        }
        return buildHeader(version, blocksPerFrame, finalFrameBlocks, totalFrames, channels, sampleRate, bits, formatFlags)
    }

    private fun buildHeader(
        version: Int,
        blocksPerFrame: Long,
        finalFrameBlocks: Long,
        totalFrames: Long,
        channels: Int,
        sampleRate: Int,
        bits: Int,
        formatFlags: Int
    ): HeaderInfo? {
        if (totalFrames < 1 || totalFrames > (1L shl 26)) return null
        if (blocksPerFrame < 1 || blocksPerFrame > 10_000_000L) return null
        if (finalFrameBlocks < 0 || finalFrameBlocks > blocksPerFrame) return null
        if (channels < 1 || channels > 32) return null
        if (sampleRate < 1 || sampleRate > 1_000_000) return null
        if (bits != 8 && bits != 16 && bits != 24 && bits != 32) return null
        val totalBlocks = (totalFrames - 1) * blocksPerFrame + finalFrameBlocks
        val durationMs = totalBlocks * 1000L / sampleRate
        return HeaderInfo(durationMs, sampleRate, channels, bits)
    }

    private class ApeTagItems(
        val entries: Map<String, String>,
        val coverBytes: ByteArray?
    )

    private fun readApeTagRegion(raf: RandomAccessFile, fileLen: Long, probe: ByteArray): ApeTagItems? {
        val probeStart = fileLen - probe.size
        var end = probe.size
        if (end >= ID3V1_BYTES && probe[end - 128] == 'T'.code.toByte() &&
            probe[end - 127] == 'A'.code.toByte() && probe[end - 126] == 'G'.code.toByte()
        ) {
            end -= ID3V1_BYTES
        }
        if (end < APE_FOOTER_BYTES) return null
        val footer = end - APE_FOOTER_BYTES
        if (!hasApeTagMagic(probe, footer)) return null
        val size = readU32(probe, footer + 12) ?: return null
        val itemCount = readU32(probe, footer + 16)?.toInt() ?: return null
        val flags = readU32(probe, footer + 20) ?: return null
        if (size < APE_FOOTER_BYTES || size > MAX_TAG_BYTES) return null
        if (itemCount < 0 || itemCount > 65535) return null
        val itemsBytes = size - APE_FOOTER_BYTES
        if (itemsBytes <= 0) return ApeTagItems(emptyMap(), null)
        val footerFileOff = probeStart + footer
        val itemsFileOff = footerFileOff - itemsBytes
        if (itemsFileOff < 0) return null
        val body = ByteArray(itemsBytes.toInt())
        raf.seek(itemsFileOff)
        raf.readFully(body)
        return parseApeItems(body, itemCount)
    }

    private fun parseApeItems(body: ByteArray, itemCount: Int): ApeTagItems? {
        val entries = LinkedHashMap<String, String>()
        var cover: ByteArray? = null
        var pos = 0
        for (i in 0 until itemCount) {
            if (pos + 8 > body.size) break
            val valueSize = readU32(body, pos) ?: break
            val flags = readU32(body, pos + 4) ?: break
            pos += 8
            var keyEnd = pos
            while (keyEnd < body.size && body[keyEnd] != 0.toByte()) keyEnd++
            if (keyEnd >= body.size) break
            val key = String(body, pos, keyEnd - pos, Charsets.UTF_8)
            val valueStart = keyEnd + 1
            if (valueSize < 0 || valueStart + valueSize > body.size) break
            val valueLen = valueSize.toInt()
            if ((flags and 6L) == 2L) {
                if (cover == null && isCoverKey(key) && valueLen > 0) {
                    var nameEnd = valueStart
                    val valueLimit = valueStart + valueLen
                    while (nameEnd < valueLimit && body[nameEnd] != 0.toByte()) nameEnd++
                    if (nameEnd < valueLimit) {
                        val dataStart = nameEnd + 1
                        cover = body.copyOfRange(dataStart, valueLimit)
                    }
                }
            } else {
                val value = String(body, valueStart, valueLen, Charsets.UTF_8).trimEnd { it == ' ' || it == '\u0000' }
                if (value.isNotEmpty()) {
                    entries[key.uppercase(Locale.ROOT)] = value
                }
            }
            pos = valueStart + valueLen
        }
        return ApeTagItems(entries, cover)
    }

    private fun isCoverKey(key: String): Boolean {
        val lower = key.lowercase(Locale.ROOT)
        return lower == "cover art (front)" || lower == "cover art (back)"
    }

    private class Id3v1Tag(
        val title: String?,
        val artist: String?,
        val album: String?,
        val year: Int,
        val track: Int
    )

    private fun parseId3v1(probe: ByteArray): Id3v1Tag? {
        if (probe.size < ID3V1_BYTES) return null
        val off = probe.size - ID3V1_BYTES
        if (probe[off] != 'T'.code.toByte() || probe[off + 1] != 'A'.code.toByte() || probe[off + 2] != 'G'.code.toByte()) {
            return null
        }
        val title = id3v1Text(probe, off + 3, 30)
        val artist = id3v1Text(probe, off + 33, 30)
        val album = id3v1Text(probe, off + 63, 30)
        val year = id3v1Text(probe, off + 93, 4)?.toIntOrNull() ?: 0
        var track = 0
        if (probe[off + 125] == 0.toByte() && probe[off + 126] != 0.toByte()) {
            track = probe[off + 126].toInt() and 0xFF
        }
        return Id3v1Tag(title, artist, album, year, track)
    }

    private fun id3v1Text(data: ByteArray, offset: Int, maxLength: Int): String? {
        var len = 0
        while (len < maxLength && data[offset + len] != 0.toByte()) len++
        val value = String(data, offset, len, Charsets.ISO_8859_1).trim()
        return value.ifEmpty { null }
    }

    private fun buildTag(header: HeaderInfo, tagData: ApeTagItems?, id3v1: Id3v1Tag?): ApeTag {
        val entries = tagData?.entries ?: emptyMap()
        val title = entries["TITLE"] ?: id3v1?.title
        val artist = entries["ARTIST"] ?: id3v1?.artist
        val album = entries["ALBUM"] ?: id3v1?.album
        val albumArtist = entries["ALBUMARTIST"] ?: entries["ALBUM ARTIST"]
        val year = (entries["YEAR"] ?: entries["RECORD DATE"] ?: entries["DATE"])?.let { parseYear(it) }
            ?: (id3v1?.year ?: 0)
        val track = (entries["TRACK"] ?: entries["TRACKNUMBER"])?.let { parseTrack(it) }
            ?: (id3v1?.track ?: 0)
        val lyrics = entries["LYRICS"] ?: entries["UNSYNCED LYRICS"] ?: entries["UNSYNCEDLYRICS"]
        return ApeTag(
            durationMs = header.durationMs,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            year = year,
            track = track,
            lyrics = lyrics,
            sampleRate = header.sampleRate,
            channels = header.channels,
            bitsPerSample = header.bitsPerSample,
            coverBytes = tagData?.coverBytes
        )
    }

    private fun parseYear(value: String): Int {
        val digits = value.takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    private fun parseTrack(value: String): Int {
        val first = value.substringBefore('/').trim()
        return first.toIntOrNull() ?: 0
    }

    private fun hasApeTagMagic(data: ByteArray, offset: Int): Boolean {
        val magic = byteArrayOf('A'.code.toByte(), 'P'.code.toByte(), 'E'.code.toByte(), 'T'.code.toByte(), 'A'.code.toByte(), 'G'.code.toByte(), 'E'.code.toByte(), 'X'.code.toByte())
        for (i in magic.indices) {
            if (data[offset + i] != magic[i]) return false
        }
        return true
    }

    private fun readU16(data: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 2 > data.size) return null
        return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readU32(data: ByteArray, offset: Int): Long? {
        if (offset < 0 || offset + 4 > data.size) return null
        return (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)
    }
}
