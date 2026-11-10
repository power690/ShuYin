/*
 * Copyright (C) 2026 The media3-decoder-ape Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.eugenedibtsev.media3.ape;

import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.UnstableApi;

/**
 * Holder for the parsed geometry of a Monkey's Audio (APE) container.
 *
 * <p>Covers both the legacy layout (encoder versions up to 3.97, {@code APE_HEADER_OLD}) and the
 * descriptor layout (3.98 and later, {@code APE_DESCRIPTOR} + {@code APE_HEADER}). All offsets are
 * relative to the {@code 'MAC '} magic, i.e. they exclude any ID3v2/junk data that may precede the
 * container in the stream.
 */
@UnstableApi
public final class ApeStreamMetadata {

  /** Version of the APE file multiplied by 1000, e.g. 3990 for 3.99. */
  public final int version;

  /** Whether the container uses the 3.98+ descriptor layout. */
  /* package */ final boolean usesDescriptorLayout;

  /** Compression level, 1000 (Fast) to 5000 (Insane). */
  public final int compressionLevel;

  /** Format flags, see {@code APE_FORMAT_FLAG_*} in MACLib. */
  /* package */ final int formatFlags;

  /** Number of audio channels. */
  public final int channelCount;

  /** Sample rate in Hertz. */
  public final int sampleRate;

  /** Bits per sample: 8, 16, 24 or 32. */
  public final int bitsPerSample;

  /** Number of audio blocks in every frame except possibly the last one. */
  /* package */ final long blocksPerFrame;

  /** Number of audio blocks in the last frame. */
  /* package */ final long finalFrameBlocks;

  /** Total number of frames. */
  /* package */ final int totalFrames;

  /** Total number of audio blocks, derived from the frame counts. */
  public final long totalBlocks;

  /** Size in bytes of the original file's data trailing the audio (not including tags). */
  /* package */ final long terminatingBytes;

  /**
   * Byte offset of each frame relative to the {@code 'MAC '} magic, from the container seek table.
   */
  /* package */ final long[] seekTable;

  /**
   * The raw container bytes preceding the frame data: header(s), optional peak level and seek
   * element count, stored WAV header and seek table(s). Served to the native decoder as the head
   * of the virtual file.
   */
  /* package */ final byte[] prefix;

  /** Number of ID3v2/junk bytes preceding the {@code 'MAC '} magic in the physical stream. */
  /* package */ final long junkBytes;

  // Format flag values, mirroring MACLib.h.
  /* package */ static final int FLAG_8_BIT = 1;
  /* package */ static final int FLAG_CRC = 1 << 1;
  /* package */ static final int FLAG_HAS_PEAK_LEVEL = 1 << 2;
  /* package */ static final int FLAG_24_BIT = 1 << 3;
  /* package */ static final int FLAG_HAS_SEEK_ELEMENTS = 1 << 4;
  /* package */ static final int FLAG_CREATE_WAV_HEADER = 1 << 5;
  /* package */ static final int FLAG_FLOATING_POINT = 1 << 12;

  private static final int COMPRESSION_LEVEL_EXTRA_HIGH = 4000;
  private static final int MAX_TOTAL_FRAMES = 1 << 26;
  private static final int MAX_BLOCKS_PER_FRAME = 10_000_000;
  private static final int MAX_WAV_HEADER_BYTES = 1 << 20;
  private static final int MAX_CHANNELS = 32;

  private ApeStreamMetadata(
      int version,
      boolean usesDescriptorLayout,
      int compressionLevel,
      int formatFlags,
      int channelCount,
      int sampleRate,
      int bitsPerSample,
      long blocksPerFrame,
      long finalFrameBlocks,
      int totalFrames,
      long terminatingBytes,
      long[] seekTable,
      byte[] prefix,
      long junkBytes) {
    this.version = version;
    this.usesDescriptorLayout = usesDescriptorLayout;
    this.compressionLevel = compressionLevel;
    this.formatFlags = formatFlags;
    this.channelCount = channelCount;
    this.sampleRate = sampleRate;
    this.bitsPerSample = bitsPerSample;
    this.blocksPerFrame = blocksPerFrame;
    this.finalFrameBlocks = finalFrameBlocks;
    this.totalFrames = totalFrames;
    this.totalBlocks = (totalFrames - 1) * blocksPerFrame + finalFrameBlocks;
    this.terminatingBytes = terminatingBytes;
    this.seekTable = seekTable;
    this.prefix = prefix;
    this.junkBytes = junkBytes;
  }

  /** Returns the duration of the stream in microseconds. */
  public long getDurationUs() {
    return totalBlocks * C.MICROS_PER_SECOND / sampleRate;
  }

  /** Returns the number of bytes per audio block (all channels of one sample). */
  public int getBlockAlign() {
    return (bitsPerSample / 8) * channelCount;
  }

  /** Returns whether the stream carries IEEE float samples. */
  public boolean isFloatingPoint() {
    return (formatFlags & FLAG_FLOATING_POINT) != 0;
  }

  /** Returns the number of frames in the stream. */
  public int getFrameCount() {
    return totalFrames;
  }

  /**
   * Returns the compressed size in bytes of frame {@code frameIndex}, derived from consecutive
   * seek table entries.
   *
   * <p>Returns {@link C#LENGTH_UNSET} for the last frame: the container does not record where its
   * audio region ends — that requires the physical file length and the size of any trailing tags,
   * neither of which is known to this parsed header. Callers estimating per-frame sizes (for
   * example, to draw a cheap loudness envelope) should reuse the previous frame's size for the
   * tail.
   *
   * @throws IndexOutOfBoundsException If {@code frameIndex} is not in {@code [0,
   *     getFrameCount())}.
   */
  public long getFrameCompressedBytes(int frameIndex) {
    if (frameIndex < 0 || frameIndex >= totalFrames) {
      throw new IndexOutOfBoundsException("frame " + frameIndex + " of " + totalFrames);
    }
    if (frameIndex == totalFrames - 1) {
      return C.LENGTH_UNSET;
    }
    return seekTable[frameIndex + 1] - seekTable[frameIndex];
  }

  /**
   * Returns the position, relative to the {@code 'MAC '} magic, at which MACLib starts reading
   * frame {@code frameIndex}.
   *
   * <p>The bitstream is consumed from a grid of 32-bit words anchored at the first seek table
   * entry, so for versions above 3.80 the read position of a frame is its seek byte aligned down
   * to that grid. Versions up to 3.80 store word-aligned seek bytes and start reading at them
   * directly.
   */
  /* package */ long getFrameReadPosition(int frameIndex) {
    long seekByte = seekTable[frameIndex];
    if (version > 3800) {
      seekByte -= (seekByte - seekTable[0]) % 4;
    }
    return seekByte;
  }

  /** Returns the index of the frame containing the given audio block. */
  /* package */ int getFrameIndexForBlock(long block) {
    long frame = block / blocksPerFrame;
    return (int) Math.min(frame, totalFrames - 1);
  }

  /**
   * Parses a complete container prefix.
   *
   * @param prefix The container bytes starting at the {@code 'MAC '} magic and extending at least
   *     to the start of the frame data. Retained by the returned instance.
   * @param junkBytes The number of stream bytes preceding the magic.
   * @return The parsed metadata.
   * @throws ParserException If the data is not a supported APE container.
   */
  public static ApeStreamMetadata parse(byte[] prefix, long junkBytes) throws ParserException {
    if (prefix.length < 32 || !hasApeMagic(prefix)) {
      throw malformed("missing 'MAC ' magic");
    }
    int version = readU16(prefix, 4);
    return version >= 3980
        ? parseDescriptorLayout(prefix, junkBytes, version)
        : parseLegacyLayout(prefix, junkBytes, version);
  }

  /**
   * Returns the total prefix size for a container whose first {@code available} bytes are given,
   * or {@link C#LENGTH_UNSET} if more data is needed to determine it. The caller grows the buffer
   * and retries until the size is known.
   *
   * @throws ParserException If the data is not a supported APE container.
   */
  public static int peekPrefixSize(byte[] data, int available) throws ParserException {
    if (available < 32) {
      return C.LENGTH_UNSET;
    }
    if (!hasApeMagic(data)) {
      throw malformed("missing 'MAC ' magic");
    }
    int version = readU16(data, 4);
    checkRange(version, 1000, 9999, "version");

    if (version >= 3980) {
      long descriptorBytes = readU32(data, 8);
      long headerBytes = readU32(data, 12);
      long seekTableBytes = readU32(data, 16);
      long wavHeaderBytes = readU32(data, 20);
      checkRange(descriptorBytes, 52, 4096, "descriptor size");
      checkRange(headerBytes, 24, 4096, "header size");
      checkRange(seekTableBytes, 0, (long) MAX_TOTAL_FRAMES * 4, "seek table size");
      checkRange(wavHeaderBytes, 0, MAX_WAV_HEADER_BYTES, "WAV header size");
      long total = descriptorBytes + headerBytes + seekTableBytes + wavHeaderBytes;
      return checkedIntPrefix(total);
    }

    // Legacy layout: the size depends on flag-dependent fields.
    int formatFlags = readU16(data, 8);
    long wavHeaderBytes = readU32(data, 16);
    long totalFrames = readU32(data, 24);
    checkRange(totalFrames, 1, MAX_TOTAL_FRAMES, "total frames");
    if ((formatFlags & FLAG_CREATE_WAV_HEADER) != 0) {
      wavHeaderBytes = 0;
    }
    checkRange(wavHeaderBytes, 0, MAX_WAV_HEADER_BYTES, "WAV header size");
    long offset = 32;
    if ((formatFlags & FLAG_HAS_PEAK_LEVEL) != 0) {
      offset += 4;
    }
    long seekElements = totalFrames;
    if ((formatFlags & FLAG_HAS_SEEK_ELEMENTS) != 0) {
      if (available < offset + 4) {
        return C.LENGTH_UNSET;
      }
      seekElements = readU32(data, (int) offset);
      checkRange(seekElements, 0, MAX_TOTAL_FRAMES, "seek elements");
      offset += 4;
    }
    long total = offset + wavHeaderBytes + seekElements * 4;
    if (version <= 3800) {
      total += seekElements; // seek bit table, one byte per element
    }
    return checkedIntPrefix(total);
  }

  private static ApeStreamMetadata parseDescriptorLayout(
      byte[] prefix, long junkBytes, int version) throws ParserException {
    int descriptorBytes = (int) readU32(prefix, 8);
    int headerBytes = (int) readU32(prefix, 12);
    int seekTableBytes = (int) readU32(prefix, 16);
    long terminatingBytes = readU32(prefix, 32);

    int h = descriptorBytes;
    int compressionLevel = readU16(prefix, h);
    int formatFlags = readU16(prefix, h + 2);
    long blocksPerFrame = readU32(prefix, h + 4);
    long finalFrameBlocks = readU32(prefix, h + 8);
    long totalFrames = readU32(prefix, h + 12);
    int bitsPerSample = readU16(prefix, h + 16);
    int channelCount = readU16(prefix, h + 18);
    int sampleRate = (int) readU32(prefix, h + 20);

    validateCommon(
        totalFrames, blocksPerFrame, finalFrameBlocks, channelCount, sampleRate, bitsPerSample);

    int seekEntries = seekTableBytes / 4;
    if (seekEntries < totalFrames) {
      throw malformed("seek table too short: " + seekEntries + " entries for " + totalFrames);
    }
    long[] seekTable = readSeekTable(prefix, descriptorBytes + headerBytes, (int) totalFrames);

    return new ApeStreamMetadata(
        version,
        /* usesDescriptorLayout= */ true,
        compressionLevel,
        formatFlags,
        channelCount,
        sampleRate,
        bitsPerSample,
        blocksPerFrame,
        finalFrameBlocks,
        (int) totalFrames,
        terminatingBytes,
        seekTable,
        prefix,
        junkBytes);
  }

  private static ApeStreamMetadata parseLegacyLayout(byte[] prefix, long junkBytes, int version)
      throws ParserException {
    int compressionLevel = readU16(prefix, 6);
    int formatFlags = readU16(prefix, 8);
    int channelCount = readU16(prefix, 10);
    int sampleRate = (int) readU32(prefix, 12);
    long wavHeaderBytes = readU32(prefix, 16);
    long terminatingBytes = readU32(prefix, 20);
    long totalFrames = readU32(prefix, 24);
    long finalFrameBlocks = readU32(prefix, 28);
    int bitsPerSample =
        (formatFlags & FLAG_8_BIT) != 0 ? 8 : (formatFlags & FLAG_24_BIT) != 0 ? 24 : 16;
    if ((formatFlags & FLAG_CREATE_WAV_HEADER) != 0) {
      wavHeaderBytes = 0;
    }

    // Frame size by version, verified against the corpus for the 9216 and 73728 branches; the
    // 294912 branch (3.95-3.97) follows APEHeader.cpp:381-382.
    long blocksPerFrame;
    if (version >= 3950) {
      blocksPerFrame = 73728 * 4;
    } else if (version >= 3900
        || (version >= 3800 && compressionLevel == COMPRESSION_LEVEL_EXTRA_HIGH)) {
      blocksPerFrame = 73728;
    } else {
      blocksPerFrame = 9216;
    }

    validateCommon(
        totalFrames, blocksPerFrame, finalFrameBlocks, channelCount, sampleRate, bitsPerSample);

    int offset = 32;
    if ((formatFlags & FLAG_HAS_PEAK_LEVEL) != 0) {
      offset += 4;
    }
    long seekElements = totalFrames;
    if ((formatFlags & FLAG_HAS_SEEK_ELEMENTS) != 0) {
      seekElements = readU32(prefix, offset);
      offset += 4;
    }
    if (seekElements < totalFrames) {
      throw malformed("seek table too short: " + seekElements + " entries for " + totalFrames);
    }
    // The stored WAV header precedes the seek tables (verified on corpus files; the order stated
    // in some format descriptions is wrong).
    offset += (int) wavHeaderBytes;
    long[] seekTable = readSeekTable(prefix, offset, (int) totalFrames);

    return new ApeStreamMetadata(
        version,
        /* usesDescriptorLayout= */ false,
        compressionLevel,
        formatFlags,
        channelCount,
        sampleRate,
        bitsPerSample,
        blocksPerFrame,
        finalFrameBlocks,
        (int) totalFrames,
        terminatingBytes,
        seekTable,
        prefix,
        junkBytes);
  }

  private static void validateCommon(
      long totalFrames,
      long blocksPerFrame,
      long finalFrameBlocks,
      int channelCount,
      int sampleRate,
      int bitsPerSample)
      throws ParserException {
    checkRange(totalFrames, 1, MAX_TOTAL_FRAMES, "total frames");
    checkRange(blocksPerFrame, 1, MAX_BLOCKS_PER_FRAME, "blocks per frame");
    checkRange(finalFrameBlocks, 0, blocksPerFrame, "final frame blocks");
    checkRange(channelCount, 1, MAX_CHANNELS, "channel count");
    checkRange(sampleRate, 1, 1_000_000, "sample rate");
    if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24 && bitsPerSample != 32) {
      throw malformed("unsupported bits per sample: " + bitsPerSample);
    }
  }

  private static long[] readSeekTable(byte[] prefix, int offset, int totalFrames)
      throws ParserException {
    if (offset + 4L * totalFrames > prefix.length) {
      throw malformed("prefix truncated before seek table end");
    }
    long[] seekTable = new long[totalFrames];
    long previous = -1;
    long wrapAdd = 0;
    for (int i = 0; i < totalFrames; i++) {
      long entry = readU32(prefix, offset + 4 * i);
      // The stored entries are 32-bit; MACLib un-wraps them for files over 4 GB the same way.
      if (previous != -1 && entry < (previous - wrapAdd)) {
        wrapAdd += 1L << 32;
      }
      entry += wrapAdd;
      if (previous != -1 && entry < previous) {
        throw malformed("seek table not monotonic at entry " + i);
      }
      seekTable[i] = entry;
      previous = entry;
    }
    if (seekTable[0] < offset + 4L * totalFrames) {
      throw malformed("first frame offset overlaps the header");
    }
    return seekTable;
  }

  private static boolean hasApeMagic(byte[] data) {
    return data[0] == 'M' && data[1] == 'A' && data[2] == 'C' && (data[3] == ' ' || data[3] == 'F');
  }

  private static int checkedIntPrefix(long total) throws ParserException {
    if (total < 32 || total > Integer.MAX_VALUE - 8) {
      throw malformed("implausible prefix size: " + total);
    }
    return (int) total;
  }

  private static void checkRange(long value, long min, long max, String name)
      throws ParserException {
    if (value < min || value > max) {
      throw malformed(name + " out of range: " + value);
    }
  }

  private static ParserException malformed(String message) {
    return ParserException.createForMalformedContainer(message, /* cause= */ null);
  }

  private static int readU16(byte[] data, int offset) {
    return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
  }

  private static long readU32(byte[] data, int offset) {
    return (data[offset] & 0xFFL)
        | ((data[offset + 1] & 0xFFL) << 8)
        | ((data[offset + 2] & 0xFFL) << 16)
        | ((data[offset + 3] & 0xFFL) << 24);
  }
}
