/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2026 The media3-decoder-ape Authors
 *
 * Modified from the AndroidX Media3 FLAC extension.
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

import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.metadata.id3.Id3Decoder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Extracts and decodes data from a Monkey's Audio (APE) container using the official MACLib
 * decoder.
 *
 * <p>Decoding happens inside the extractor and raw PCM is emitted to the {@link TrackOutput},
 * mirroring the approach of Media3's own native {@code FlacExtractor}. Seeking is driven by the
 * container's seek table and is sample-accurate: MACLib positions within a frame by
 * decode-and-discard.
 *
 * <p>Only streams of known length are supported: the APE format stores its tags at the end of the
 * file and MACLib requires the total size to locate the last frame.
 */
@UnstableApi
public final class ApeExtractor implements Extractor {

  /** Factory that returns one extractor which is an {@link ApeExtractor}. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new ApeExtractor()};

  /** MIME type for the APE container. Media3 has no built-in constant for it. */
  public static final String MIME_TYPE_APE = "audio/x-ape";

  /** The maximum APE tag size read for metadata; larger tags are skipped but still accounted. */
  private static final int MAX_TAG_BYTES_READ = 8 * 1024 * 1024;

  /** The number of audio blocks decoded per {@link #read} call. */
  private static final int DECODE_CHUNK_BLOCKS = 4096;

  private static final long NO_PENDING_SEEK = -1;

  /** Size of an ID3v2 header, and of the optional footer that mirrors it. */
  private static final int ID3_HEADER_LENGTH = 10;

  private static final int STATE_READ_ID3 = 0;
  private static final int STATE_READ_HEADER = 1;
  private static final int STATE_READ_TAG_FOOTER = 2;
  private static final int STATE_READ_TAG_BODY = 3;
  private static final int STATE_CREATE_DECODER = 4;
  private static final int STATE_PRIME_DECODER = 5;
  private static final int STATE_DECODE = 6;

  private int state;
  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;

  @Nullable private Metadata id3Metadata;
  private long junkBytes;
  private ApeStreamMetadata streamMetadata;
  private long fileLength;
  private long tagRegionStart;
  @Nullable private ApeTagReader.TagInfo tagInfo;
  private final List<Metadata.Entry> tagEntries;
  private long virtualSize;

  @Nullable private ApeDecoderJni decoderJni;
  private ByteBuffer outputBuffer;
  private ParsableByteArray outputScratch;

  private long currentBlock;
  private long pendingSeekBlock;
  private boolean streamOutputsEmitted;

  /** Loader thread whose priority was raised for Insane decoding; 0 when nothing to restore. */
  private int priorityBoostedTid;

  private int originalThreadPriority;

  public ApeExtractor() {
    tagEntries = new ArrayList<>();
    pendingSeekBlock = NO_PENDING_SEEK;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    peekPastId3(input);
    byte[] scratch = new byte[6];
    if (!input.peekFully(scratch, 0, 6, /* allowEndOfInput= */ true)) {
      return false;
    }
    if (scratch[0] != 'M'
        || scratch[1] != 'A'
        || scratch[2] != 'C'
        || (scratch[3] != ' ' && scratch[3] != 'F')) {
      return false;
    }
    int version = (scratch[4] & 0xFF) | ((scratch[5] & 0xFF) << 8);
    return version >= 1000 && version <= 9999;
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = output.track(/* id= */ 0, C.TRACK_TYPE_AUDIO);
    output.endTracks();
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    switch (state) {
      case STATE_READ_ID3:
        return readId3(input);
      case STATE_READ_HEADER:
        return readHeader(input, seekPosition);
      case STATE_READ_TAG_FOOTER:
        return readTagFooter(input, seekPosition);
      case STATE_READ_TAG_BODY:
        return readTagBody(input, seekPosition);
      case STATE_CREATE_DECODER:
        return createDecoder(seekPosition);
      case STATE_PRIME_DECODER:
        return primeDecoder(input, seekPosition);
      case STATE_DECODE:
        return decode(input, seekPosition);
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  public void seek(long position, long timeUs) {
    if (streamMetadata == null || decoderJni == null) {
      // Not initialized yet: restart parsing from scratch if the stream restarts.
      if (position == 0) {
        state = STATE_READ_ID3;
      }
      pendingSeekBlock =
          streamMetadata != null ? blockForTimeUs(streamMetadata, timeUs) : NO_PENDING_SEEK;
      return;
    }
    pendingSeekBlock = blockForTimeUs(streamMetadata, timeUs);
  }

  @Override
  public void release() {
    if (decoderJni != null) {
      decoderJni.release();
      decoderJni = null;
    }
    if (priorityBoostedTid != 0) {
      // The library must not permanently change the priority of a thread it does not own.
      try {
        android.os.Process.setThreadPriority(priorityBoostedTid, originalThreadPriority);
      } catch (RuntimeException e) {
        // The loader thread is already gone; nothing left to restore.
      }
      priorityBoostedTid = 0;
    }
  }

  /**
   * Peeks past any ID3v2 tags at the head of the stream, leaving the peek position at the first
   * byte after them.
   *
   * <p>Media3 has equivalent helpers, but only behind {@code @UnstableApi} signatures that have
   * changed between releases; since {@code media3-extractor} is an {@code api} dependency here, a
   * consumer upgrading Media3 would hit a {@link NoSuchMethodError}. The ID3v2 framing is four
   * lines of well-specified parsing, so it is done here instead. Tag <em>contents</em> still go
   * through {@link Id3Decoder}, whose {@code decode(byte[], int)} entry point is stable.
   */
  private static int peekPastId3(ExtractorInput input) throws IOException {
    byte[] header = new byte[ID3_HEADER_LENGTH];
    int totalBytes = 0;
    while (true) {
      input.resetPeekPosition();
      input.advancePeekPosition(totalBytes);
      if (!input.peekFully(header, 0, ID3_HEADER_LENGTH, /* allowEndOfInput= */ true)
          || header[0] != 'I'
          || header[1] != 'D'
          || header[2] != '3') {
        input.resetPeekPosition();
        input.advancePeekPosition(totalBytes);
        return totalBytes;
      }
      // Bytes 6-9 are a synchsafe integer: seven bits per byte, high bit always clear.
      int dataLength =
          ((header[6] & 0x7F) << 21)
              | ((header[7] & 0x7F) << 14)
              | ((header[8] & 0x7F) << 7)
              | (header[9] & 0x7F);
      // Flag bit 4 of byte 5 marks a 10-byte footer duplicating the header.
      boolean hasFooter = (header[5] & 0x10) != 0;
      totalBytes += ID3_HEADER_LENGTH + dataLength + (hasFooter ? ID3_HEADER_LENGTH : 0);
    }
  }

  private int readId3(ExtractorInput input) throws IOException {
    int id3Bytes = peekPastId3(input);
    if (id3Bytes > 0) {
      byte[] id3Data = new byte[id3Bytes];
      input.readFully(id3Data, 0, id3Bytes);
      id3Metadata = new Id3Decoder().decode(id3Data, id3Bytes);
    }
    junkBytes = input.getPosition();
    state = STATE_READ_HEADER;
    return RESULT_CONTINUE;
  }

  private int readHeader(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    // Grow the buffer until the flag-dependent prefix size is known, then read the whole prefix.
    byte[] buffer = new byte[64];
    int filled = 0;
    int prefixSize = C.LENGTH_UNSET;
    while (prefixSize == C.LENGTH_UNSET) {
      int fetch = (filled == 0) ? 32 : 4;
      input.readFully(buffer, filled, fetch);
      filled += fetch;
      prefixSize = ApeStreamMetadata.peekPrefixSize(buffer, filled);
    }
    byte[] prefix = Arrays.copyOf(buffer, prefixSize);
    if (prefixSize > filled) {
      input.readFully(prefix, filled, prefixSize - filled);
    }
    streamMetadata = ApeStreamMetadata.parse(prefix, junkBytes);

    fileLength = input.getLength();
    if (fileLength == C.LENGTH_UNSET) {
      throw ParserException.createForUnsupportedContainerFeature(
          "APE requires an input of known length");
    }

    // The tags live at the end of the stream; fetch them before creating the decoder.
    long probeStart = max(junkBytes + prefixSize, fileLength - ApeTagReader.PROBE_BYTES);
    seekPosition.position = probeStart;
    state = STATE_READ_TAG_FOOTER;
    return RESULT_SEEK;
  }

  private int readTagFooter(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    ApeStreamMetadata streamMetadata = Objects.requireNonNull(this.streamMetadata);
    int probeLength = (int) (fileLength - input.getPosition());
    byte[] probe = new byte[probeLength];
    input.readFully(probe, 0, probeLength);
    tagInfo = ApeTagReader.probe(probe);
    tagEntries.addAll(tagInfo.entries);
    virtualSize = fileLength - junkBytes - tagInfo.tagBytes;

    if (tagInfo.apeItemsBytes > 0 && tagInfo.apeItemsBytes <= MAX_TAG_BYTES_READ) {
      // Items sit immediately before the 32-byte footer, which itself precedes any ID3v1 tag.
      long footerEnd = fileLength - (hasId3v1InTag(probe) ? 128 : 0);
      tagRegionStart = footerEnd - 32 - tagInfo.apeItemsBytes;
      seekPosition.position = tagRegionStart;
      state = STATE_READ_TAG_BODY;
      return RESULT_SEEK;
    }

    state = STATE_CREATE_DECODER;
    return RESULT_CONTINUE;
  }

  private int readTagBody(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    ApeTagReader.TagInfo tagInfo = Objects.requireNonNull(this.tagInfo);
    byte[] body = new byte[(int) tagInfo.apeItemsBytes];
    input.readFully(body, 0, body.length);
    ApeTagReader.parseApeItems(body, tagInfo.apeItemCount, tagEntries);
    state = STATE_CREATE_DECODER;
    return RESULT_CONTINUE;
  }

  private int createDecoder(PositionHolder seekPosition) throws IOException {
    ApeStreamMetadata streamMetadata = Objects.requireNonNull(this.streamMetadata);
    TrackOutput trackOutput = Objects.requireNonNull(this.trackOutput);
    ExtractorOutput extractorOutput = Objects.requireNonNull(this.extractorOutput);

    if (!ApeLibrary.isAvailable()) {
      throw ParserException.createForUnsupportedContainerFeature(
          "the APE native decoder library (libapeJNI) is not available for this device's ABIs "
              + Arrays.toString(android.os.Build.SUPPORTED_ABIS)
              + "; check ApeLibrary.isAvailable() before attempting playback");
    }

    if (streamMetadata.compressionLevel >= 5000 && priorityBoostedTid == 0) {
      // Insane is by far the heaviest level and its frames are 26.7s long, so a stall here costs
      // a whole frame. Loader threads run at background priority, which on big.LITTLE devices
      // confines them to the little cores; decoding is real-time audio work, so claim the audio
      // priority the way an audio thread would. Kept as a safety margin: the level decodes at a
      // realtime factor of 0.042 with the priority pinned, and it has not been measured without.
      // The original priority is restored in release().
      priorityBoostedTid = android.os.Process.myTid();
      originalThreadPriority = android.os.Process.getThreadPriority(priorityBoostedTid);
      android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
    }

    try {
      decoderJni =
          new ApeDecoderJni(streamMetadata.prefix, virtualSize, decoderThreadsFor(streamMetadata));
    } catch (ApeDecoderException e) {
      throw ParserException.createForMalformedContainer("MACLib rejected the stream", e);
    }

    int chunkBytes = DECODE_CHUNK_BLOCKS * streamMetadata.getBlockAlign();
    outputBuffer = ByteBuffer.allocateDirect(chunkBytes).order(ByteOrder.LITTLE_ENDIAN);
    outputScratch = new ParsableByteArray(chunkBytes);

    if (!streamOutputsEmitted) {
      trackOutput.format(buildFormat(streamMetadata));
      extractorOutput.seekMap(new ApeSeekMap(streamMetadata));
      streamOutputsEmitted = true;
    }

    // MACLib initialization must happen with the input at the first frame: the pre-3.93 decoder
    // decodes frame 0 as part of its initialization.
    seekPosition.position = junkBytes + streamMetadata.seekTable[0];
    state = STATE_PRIME_DECODER;
    return RESULT_SEEK;
  }

  private int primeDecoder(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    ApeStreamMetadata streamMetadata = Objects.requireNonNull(this.streamMetadata);
    ApeDecoderJni decoderJni = Objects.requireNonNull(this.decoderJni);
    ByteBuffer outputBuffer = Objects.requireNonNull(this.outputBuffer);

    // A user seek issued while initialization was in flight repositions the input; MACLib
    // initialization must nevertheless happen at the first frame (the pre-3.93 decoder decodes
    // frame 0 there), so keep requesting that position until it is provided.
    long firstFramePosition = junkBytes + streamMetadata.seekTable[0];
    if (input.getPosition() != firstFramePosition) {
      seekPosition.position = firstFramePosition;
      return RESULT_SEEK;
    }

    decoderJni.setData(input);
    decoderJni.reposition(input.getPosition() - junkBytes);
    try {
      decoderJni.decode(outputBuffer, /* maxBlocks= */ 0);
    } catch (ApeDecoderException e) {
      throw ParserException.createForMalformedContainer("APE decoder initialization failed", e);
    } catch (IOException e) {
      invalidateDecoderForRetry();
      throw e;
    }
    currentBlock = 0;
    state = STATE_DECODE;
    if (pendingSeekBlock != NO_PENDING_SEEK) {
      seekPosition.position = frameReadPositionForBlock(streamMetadata, pendingSeekBlock);
      return RESULT_SEEK;
    }
    return RESULT_CONTINUE;
  }

  private int decode(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    ApeStreamMetadata streamMetadata = Objects.requireNonNull(this.streamMetadata);
    ApeDecoderJni decoderJni = Objects.requireNonNull(this.decoderJni);
    ByteBuffer outputBuffer = Objects.requireNonNull(this.outputBuffer);
    ParsableByteArray outputScratch = Objects.requireNonNull(this.outputScratch);
    TrackOutput trackOutput = Objects.requireNonNull(this.trackOutput);

    decoderJni.setData(input);

    if (pendingSeekBlock != NO_PENDING_SEEK) {
      long expectedPosition = frameReadPositionForBlock(streamMetadata, pendingSeekBlock);
      if (input.getPosition() != expectedPosition) {
        seekPosition.position = expectedPosition;
        return RESULT_SEEK;
      }
      decoderJni.reposition(input.getPosition() - junkBytes);
      try {
        decoderJni.seek(pendingSeekBlock);
      } catch (ApeDecoderException e) {
        throw ParserException.createForMalformedContainer("APE seek failed", e);
      } catch (IOException e) {
        // Keep pendingSeekBlock: the retry lands on the same target through a fresh decoder.
        invalidateDecoderForRetry();
        throw e;
      }
      currentBlock = pendingSeekBlock;
      pendingSeekBlock = NO_PENDING_SEEK;
    }

    long blocksRemaining = streamMetadata.totalBlocks - currentBlock;
    if (blocksRemaining <= 0) {
      return RESULT_END_OF_INPUT;
    }

    int blocksDecoded;
    try {
      blocksDecoded =
          decoderJni.decode(outputBuffer, (int) min(DECODE_CHUNK_BLOCKS, blocksRemaining));
    } catch (ApeDecoderException e) {
      throw ParserException.createForMalformedContainer(
          "APE decode failed at block " + currentBlock, e);
    } catch (IOException e) {
      // Allow the load to be retried: restart decoding at the current frame boundary.
      pendingSeekBlock = currentBlock;
      invalidateDecoderForRetry();
      input.setRetryPosition(frameReadPositionForBlock(streamMetadata, currentBlock), e);
      throw e; // Unreachable: setRetryPosition throws e. Kept for definite behavior.
    }

    if (blocksDecoded == 0) {
      return RESULT_END_OF_INPUT;
    }

    int sizeBytes = blocksDecoded * streamMetadata.getBlockAlign();
    outputBuffer.position(0);
    outputBuffer.get(outputScratch.getData(), 0, sizeBytes);
    outputScratch.setPosition(0);
    trackOutput.sampleData(outputScratch, sizeBytes);
    long timeUs = currentBlock * C.MICROS_PER_SECOND / streamMetadata.sampleRate;
    trackOutput.sampleMetadata(
        timeUs, C.BUFFER_FLAG_KEY_FRAME, sizeBytes, /* offset= */ 0, /* cryptoData= */ null);

    currentBlock += blocksDecoded;
    return currentBlock >= streamMetadata.totalBlocks ? RESULT_END_OF_INPUT : RESULT_CONTINUE;
  }

  private Format buildFormat(ApeStreamMetadata streamMetadata) {
    @C.PcmEncoding int pcmEncoding;
    if (streamMetadata.isFloatingPoint()) {
      pcmEncoding = C.ENCODING_PCM_FLOAT;
    } else {
      pcmEncoding = Util.getPcmEncoding(streamMetadata.bitsPerSample);
    }

    @Nullable Metadata metadata = id3Metadata;
    if (!tagEntries.isEmpty()) {
      Metadata tagMetadata = new Metadata(tagEntries);
      metadata = metadata == null ? tagMetadata : metadata.copyWithAppendedEntriesFrom(tagMetadata);
    }

    long durationUs = streamMetadata.getDurationUs();
    int averageBitrate =
        durationUs > 0 ? (int) (fileLength * 8 * C.MICROS_PER_SECOND / durationUs) : Format.NO_VALUE;

    return new Format.Builder()
        .setContainerMimeType(MIME_TYPE_APE)
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setChannelCount(streamMetadata.channelCount)
        .setSampleRate(streamMetadata.sampleRate)
        .setPcmEncoding(pcmEncoding)
        .setAverageBitrate(averageBitrate)
        .setMaxInputSize(DECODE_CHUNK_BLOCKS * streamMetadata.getBlockAlign())
        .setMetadata(metadata)
        .build();
  }

  /** Overrides the MACLib worker count when positive. For performance experiments only. */
  @androidx.annotation.VisibleForTesting /* package */ static int decoderThreadOverride = 0;

  private static int decoderThreadsFor(ApeStreamMetadata streamMetadata) {
    if (decoderThreadOverride > 0) {
      return decoderThreadOverride;
    }
    // A single core decodes every level comfortably in real time: the worst case is Insane at a
    // realtime factor of 0.042 on a Galaxy A33, a 24x margin. Workers are not worth their battery
    // cost -- MACLib's frame-level parallelism saturates around 2.5x on four threads anyway.
    return 1;
  }

  /**
   * Discards the native decoder after a failed read so the retry rebuilds it from scratch.
   *
   * <p>MACLib's frame pipeline cannot be reused after an IO error: a failed frame read posts the
   * worker's ready semaphore without a decoded frame ({@code SetErrorState} in {@code
   * APEDecompressCore.cpp}), and {@code Seek()} never drains it, so every frame delivered after
   * an in-place recovery is stale or torn. Rebuilding is cheap: the container prefix is retained
   * in memory, so no extra IO beyond re-priming at the first frame is needed.
   */
  private void invalidateDecoderForRetry() {
    if (decoderJni != null) {
      decoderJni.release();
      decoderJni = null;
    }
    state = STATE_CREATE_DECODER;
  }

  private long frameReadPositionForBlock(ApeStreamMetadata streamMetadata, long block) {
    int frame = streamMetadata.getFrameIndexForBlock(block);
    return junkBytes + streamMetadata.getFrameReadPosition(frame);
  }

  private static long blockForTimeUs(ApeStreamMetadata streamMetadata, long timeUs) {
    long block = timeUs * streamMetadata.sampleRate / C.MICROS_PER_SECOND;
    return Util.constrainValue(block, 0, streamMetadata.totalBlocks - 1);
  }

  private static boolean hasId3v1InTag(byte[] probe) {
    int end = probe.length;
    return end >= 128
        && probe[end - 128] == 'T'
        && probe[end - 127] == 'A'
        && probe[end - 126] == 'G';
  }

  /** A {@link SeekMap} based on the APE seek table. */
  private final class ApeSeekMap implements SeekMap {

    private final ApeStreamMetadata streamMetadata;

    public ApeSeekMap(ApeStreamMetadata streamMetadata) {
      this.streamMetadata = streamMetadata;
    }

    @Override
    public boolean isSeekable() {
      return true;
    }

    @Override
    public long getDurationUs() {
      return streamMetadata.getDurationUs();
    }

    @Override
    public SeekPoints getSeekPoints(long timeUs) {
      long targetBlock = blockForTimeUs(streamMetadata, timeUs);
      int frame = streamMetadata.getFrameIndexForBlock(targetBlock);
      SeekPoint before = seekPointForFrame(frame);
      if (before.timeUs >= timeUs || frame == streamMetadata.totalFrames - 1) {
        return new SeekPoints(before);
      }
      return new SeekPoints(before, seekPointForFrame(frame + 1));
    }

    private SeekPoint seekPointForFrame(int frame) {
      long frameTimeUs =
          frame * streamMetadata.blocksPerFrame * C.MICROS_PER_SECOND / streamMetadata.sampleRate;
      return new SeekPoint(frameTimeUs, junkBytes + streamMetadata.getFrameReadPosition(frame));
    }
  }
}
