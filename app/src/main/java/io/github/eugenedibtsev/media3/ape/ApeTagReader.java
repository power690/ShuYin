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

import androidx.annotation.Nullable;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.Log;
import androidx.media3.extractor.metadata.flac.PictureFrame;
import androidx.media3.extractor.metadata.vorbis.VorbisComment;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the trailing tag region of an APE stream: an optional APEv1/APEv2 tag followed by an
 * optional ID3v1 tag.
 *
 * <p>Text items are exposed as {@link VorbisComment} entries and embedded cover art as {@link
 * PictureFrame} entries, matching what Media3's FLAC integration emits, so downstream consumers
 * need no APE-specific handling.
 */
/* package */ final class ApeTagReader {

  /** Result of probing the tag region. */
  /* package */ static final class TagInfo {
    /** Total bytes occupied by tags at the end of the stream (APE tag plus ID3v1). */
    /* package */ final long tagBytes;

    /** Size in bytes of the APEv1/APEv2 tag item data, 0 if there is no APE tag. */
    /* package */ final long apeItemsBytes;

    /** Number of items in the APE tag. */
    /* package */ final int apeItemCount;

    /** Entries parsed so far (from ID3v1); APE tag items are parsed separately from the body. */
    /* package */ final List<Metadata.Entry> entries;

    /* package */ TagInfo(
        long tagBytes, long apeItemsBytes, int apeItemCount, List<Metadata.Entry> entries) {
      this.tagBytes = tagBytes;
      this.apeItemsBytes = apeItemsBytes;
      this.apeItemCount = apeItemCount;
      this.entries = entries;
    }
  }

  private static final String TAG = "ApeTagReader";

  /** APE tag footer magic. */
  private static final byte[] APE_TAG_MAGIC = {'A', 'P', 'E', 'T', 'A', 'G', 'E', 'X'};

  private static final int APE_TAG_FOOTER_BYTES = 32;
  private static final int ID3V1_BYTES = 128;
  private static final int APE_TAG_FLAG_HAS_HEADER = 0x80000000;
  private static final int APE_TAG_FLAG_TYPE_MASK = 0x00000006;
  private static final int APE_TAG_FLAG_TYPE_BINARY = 0x00000002;

  /** The number of bytes to read from the end of the stream to find both tag footers. */
  public static final int PROBE_BYTES = ID3V1_BYTES + APE_TAG_FOOTER_BYTES;

  private ApeTagReader() {}

  /**
   * Probes the last bytes of the stream for tags.
   *
   * @param data The last {@code data.length} bytes of the stream, at least {@link #PROBE_BYTES}
   *     unless the stream is shorter.
   * @return Information about the tag region.
   */
  public static TagInfo probe(byte[] data) {
    List<Metadata.Entry> entries = new ArrayList<>();
    int end = data.length;
    long tagBytes = 0;

    if (end >= ID3V1_BYTES
        && data[end - 128] == 'T'
        && data[end - 127] == 'A'
        && data[end - 126] == 'G') {
      parseId3v1(data, end - 128, entries);
      tagBytes += ID3V1_BYTES;
      end -= ID3V1_BYTES;
    }

    long apeItemsBytes = 0;
    int apeItemCount = 0;
    if (end >= APE_TAG_FOOTER_BYTES && hasApeTagMagic(data, end - APE_TAG_FOOTER_BYTES)) {
      int footer = end - APE_TAG_FOOTER_BYTES;
      long size = readU32(data, footer + 12); // items + footer, excluding any header
      int itemCount = (int) readU32(data, footer + 16);
      long flags = readU32(data, footer + 20);
      if (size >= APE_TAG_FOOTER_BYTES) {
        apeItemsBytes = size - APE_TAG_FOOTER_BYTES;
        apeItemCount = itemCount;
        tagBytes += size + (((flags & APE_TAG_FLAG_HAS_HEADER) != 0) ? APE_TAG_FOOTER_BYTES : 0);
      }
    }

    return new TagInfo(tagBytes, apeItemsBytes, apeItemCount, entries);
  }

  /**
   * Parses APE tag items.
   *
   * @param body The item data (between the optional tag header and the footer).
   * @param itemCount The number of items, from the footer.
   * @param entries The list to append parsed entries to.
   */
  public static void parseApeItems(byte[] body, int itemCount, List<Metadata.Entry> entries) {
    int position = 0;
    for (int i = 0; i < itemCount; i++) {
      if (position + 8 > body.length) {
        return; // Truncated tag: keep what was parsed.
      }
      long valueSize = readU32(body, position);
      long flags = readU32(body, position + 4);
      position += 8;
      int keyEnd = position;
      while (keyEnd < body.length && body[keyEnd] != 0) {
        keyEnd++;
      }
      if (keyEnd >= body.length || position + (keyEnd - position) + 1 + valueSize > body.length) {
        return;
      }
      String key = new String(body, position, keyEnd - position, StandardCharsets.UTF_8);
      int valueStart = keyEnd + 1;
      int valueLength = (int) valueSize;

      if ((flags & APE_TAG_FLAG_TYPE_MASK) == APE_TAG_FLAG_TYPE_BINARY) {
        maybeAddPicture(key, body, valueStart, valueLength, entries);
      } else {
        String value = new String(body, valueStart, valueLength, StandardCharsets.UTF_8);
        entries.add(new VorbisComment(ApeCharsets.toUpperInvariant(key), value));
      }
      position = valueStart + valueLength;
    }
  }

  private static void maybeAddPicture(
      String key, byte[] body, int valueStart, int valueLength, List<Metadata.Entry> entries) {
    int pictureType;
    String lowerKey = ApeCharsets.toLowerInvariant(key);
    if (lowerKey.equals("cover art (front)")) {
      pictureType = 3; // ID3 "Cover (front)"
    } else if (lowerKey.equals("cover art (back)")) {
      pictureType = 4; // ID3 "Cover (back)"
    } else {
      return; // Other binary items have no Media3 representation.
    }

    // Binary cover art is stored as [extension or filename][NUL][image data].
    int nameEnd = valueStart;
    int valueLimit = valueStart + valueLength;
    while (nameEnd < valueLimit && body[nameEnd] != 0) {
      nameEnd++;
    }
    if (nameEnd >= valueLimit) {
      Log.w(TAG, "Malformed cover art item, skipping");
      return;
    }
    String name = new String(body, valueStart, nameEnd - valueStart, StandardCharsets.UTF_8);
    int dataStart = nameEnd + 1;
    byte[] pictureData = new byte[valueLimit - dataStart];
    System.arraycopy(body, dataStart, pictureData, 0, pictureData.length);

    String extension = name;
    int dot = name.lastIndexOf('.');
    if (dot >= 0) {
      extension = name.substring(dot + 1);
    }
    String mimeType;
    switch (ApeCharsets.toLowerInvariant(extension)) {
      case "jpg":
      case "jpeg":
        mimeType = "image/jpeg";
        break;
      case "png":
        mimeType = "image/png";
        break;
      case "gif":
        mimeType = "image/gif";
        break;
      case "bmp":
        mimeType = "image/bmp";
        break;
      default:
        mimeType = "image/unknown";
        break;
    }

    entries.add(
        new PictureFrame(
            pictureType,
            mimeType,
            /* description= */ "",
            /* width= */ 0,
            /* height= */ 0,
            /* depth= */ 0,
            /* colors= */ 0,
            pictureData));
  }

  private static void parseId3v1(byte[] data, int offset, List<Metadata.Entry> entries) {
    addId3v1Text(entries, "TITLE", data, offset + 3, 30);
    addId3v1Text(entries, "ARTIST", data, offset + 33, 30);
    addId3v1Text(entries, "ALBUM", data, offset + 63, 30);
    addId3v1Text(entries, "DATE", data, offset + 93, 4);
    // ID3v1.1: a zero byte at comment[28] marks the presence of a track number in comment[29].
    if (data[offset + 125] == 0 && data[offset + 126] != 0) {
      entries.add(new VorbisComment("TRACKNUMBER", Integer.toString(data[offset + 126] & 0xFF)));
    }
  }

  private static void addId3v1Text(
      List<Metadata.Entry> entries, String key, byte[] data, int offset, int maxLength) {
    int length = 0;
    while (length < maxLength && data[offset + length] != 0) {
      length++;
    }
    String value = new String(data, offset, length, StandardCharsets.ISO_8859_1).trim();
    if (!value.isEmpty()) {
      entries.add(new VorbisComment(key, value));
    }
  }

  private static boolean hasApeTagMagic(byte[] data, int offset) {
    for (int i = 0; i < APE_TAG_MAGIC.length; i++) {
      if (data[offset + i] != APE_TAG_MAGIC[i]) {
        return false;
      }
    }
    return true;
  }

  private static long readU32(byte[] data, int offset) {
    return (data[offset] & 0xFFL)
        | ((data[offset + 1] & 0xFFL) << 8)
        | ((data[offset + 2] & 0xFFL) << 16)
        | ((data[offset + 3] & 0xFFL) << 24);
  }

  /** Locale-independent case mapping for ASCII tag keys. */
  private static final class ApeCharsets {
    private ApeCharsets() {}

    static String toUpperInvariant(String s) {
      return s.toUpperCase(java.util.Locale.ROOT);
    }

    static String toLowerInvariant(String s) {
      return s.toLowerCase(java.util.Locale.ROOT);
    }
  }
}
