package com.xiaowei.player.data

import java.io.File
import java.io.RandomAccessFile

object FlacLyricsParser {

    private const val TAG_VENDOR = -1 
    private const val BLOCK_TYPE_VORBIS_COMMENT = 4

    private val LYRICS_FIELDS = listOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "LYRIC")

    fun readLyrics(filePath: String): String? {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null
        if (!isFlacFile(file)) return null

        return try {
            RandomAccessFile(file, "r").use { raf ->

                if (raf.readByte().toInt() != 0x66 || 
                    raf.readByte().toInt() != 0x4C || 
                    raf.readByte().toInt() != 0x61 || 
                    raf.readByte().toInt() != 0x43    
                ) return null

                var isLast = false
                while (!isLast) {
                    val headerByte = raf.readByte().toInt() and 0xFF
                    isLast = (headerByte and 0x80) != 0
                    val blockType = headerByte and 0x7F

                    val len = ((raf.readByte().toInt() and 0xFF) shl 16) or
                              ((raf.readByte().toInt() and 0xFF) shl 8) or
                              (raf.readByte().toInt() and 0xFF)

                    if (blockType == BLOCK_TYPE_VORBIS_COMMENT) {
                        val blockData = ByteArray(len)
                        raf.readFully(blockData)
                        return parseVorbisComment(blockData)
                    } else {

                        raf.seek(raf.filePointer + len)
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isFlacFile(file: File): Boolean {
        if (file.length() < 4) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.readByte().toInt() == 0x66 && 
                raf.readByte().toInt() == 0x4C && 
                raf.readByte().toInt() == 0x61 && 
                raf.readByte().toInt() == 0x43    
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun parseVorbisComment(data: ByteArray): String? {
        var pos = 0

        val vendorLen = readLeUint32(data, pos)
        pos += 4
        if (pos + vendorLen > data.size) return null
        pos += vendorLen.toInt()

        if (pos + 4 > data.size) return null
        val count = readLeUint32(data, pos).toInt()
        pos += 4

        val lyricsBuilder = StringBuilder()
        var foundAny = false

        for (i in 0 until count) {
            if (pos + 4 > data.size) break
            val commentLen = readLeUint32(data, pos).toInt()
            pos += 4
            if (pos + commentLen > data.size) break
            val comment = String(data, pos, commentLen, Charsets.UTF_8)
            pos += commentLen

            val eqIdx = comment.indexOf('=')
            if (eqIdx <= 0) continue
            val key = comment.substring(0, eqIdx).uppercase()
            val value = comment.substring(eqIdx + 1)

            if (key in LYRICS_FIELDS) {
                if (foundAny) lyricsBuilder.append('\n')
                lyricsBuilder.append(value)
                foundAny = true
            }
        }

        return if (foundAny) lyricsBuilder.toString() else null
    }

    private fun readLeUint32(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF)) or
               ((data[offset + 1].toLong() and 0xFF) shl 8) or
               ((data[offset + 2].toLong() and 0xFF) shl 16) or
               ((data[offset + 3].toLong() and 0xFF) shl 24)
    }
}
