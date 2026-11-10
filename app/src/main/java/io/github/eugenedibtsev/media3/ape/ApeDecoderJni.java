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

import static java.lang.Math.min;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.ExtractorInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * JNI wrapper for the MACLib APE decoder.
 *
 * <p>The native side owns one persistent {@code IAPEDecompress} built over a virtual file whose
 * head is the container prefix (served from memory) and whose frame region is pulled forward from
 * the current {@link ExtractorInput} through the {@link #read(ByteBuffer)} callback. The virtual
 * file is addressed relative to the {@code 'MAC '} magic and its size excludes any trailing tag
 * data, so MACLib never needs to touch the physical end of the stream.
 */
/* package */ final class ApeDecoderJni {

  private static final int TEMP_BUFFER_SIZE = 8192;

  private final long nativeContext;

  @Nullable private ExtractorInput extractorInput;
  @Nullable private byte[] tempBuffer;
  private boolean endOfExtractorInput;

  /**
   * Creates the native decoder.
   *
   * @param prefix Container bytes from the {@code 'MAC '} magic to the start of the frame data.
   * @param virtualSize Size of the virtual file: physical length minus junk and trailing tags.
   * @param threads Number of MACLib decoder threads; more than one is only worth the battery cost
   *     for streams that a single core cannot decode in real time (the Insane level).
   * @throws ApeDecoderException If the native libraries cannot be loaded or MACLib rejects the
   *     stream.
   */
  public ApeDecoderJni(byte[] prefix, long virtualSize, int threads) throws ApeDecoderException {
    if (!ApeLibrary.isAvailable()) {
      throw new ApeDecoderException("Failed to load decoder native libraries");
    }
    // The context pointer may be negative as a long (arm64 heap pointers carry a top-byte tag),
    // so failures are signaled by a zero context plus an explicit error code.
    int[] errorHolder = new int[1];
    long result = apeInit(prefix, virtualSize, threads, errorHolder);
    if (result == 0) {
      throw new ApeDecoderException("Failed to initialize decoder", errorHolder[0]);
    }
    nativeContext = result;
  }

  /** Sets the input from which the {@link #read(ByteBuffer)} callback pulls data. */
  public void setData(ExtractorInput extractorInput) {
    this.extractorInput = extractorInput;
    endOfExtractorInput = false;
    if (tempBuffer == null) {
      tempBuffer = new byte[TEMP_BUFFER_SIZE];
    }
  }

  /**
   * Reads up to {@code target.remaining()} bytes from the current input at the stream head.
   *
   * <p>Called from the native code.
   *
   * @return The number of bytes read, 0 at the end of the input, or -1 on failure.
   */
  @SuppressWarnings("unused") // Called from native code.
  public int read(ByteBuffer target) throws IOException {
    ExtractorInput extractorInput = Objects.requireNonNull(this.extractorInput);
    byte[] tempBuffer = Util.castNonNull(this.tempBuffer);
    int byteCount = min(target.remaining(), TEMP_BUFFER_SIZE);
    int read = readFromExtractorInput(extractorInput, tempBuffer, /* offset= */ 0, byteCount);
    if (read < 4) {
      // Most short reads only drain the input's internal buffer; retry once so the native side
      // is not bounced for a handful of bytes.
      read += readFromExtractorInput(extractorInput, tempBuffer, read, byteCount - read);
    }
    target.put(tempBuffer, 0, read);
    return read;
  }

  /**
   * Notifies the native side that the stream was repositioned externally (the extractor performed
   * a {@code RESULT_SEEK} round trip), invalidating the rewind ring.
   *
   * @param virtualPosition The new stream position relative to the {@code 'MAC '} magic.
   */
  public void reposition(long virtualPosition) {
    apeReposition(nativeContext, virtualPosition);
  }

  /**
   * Seeks the decoder to the exact audio block. Within-frame positioning is performed by MACLib
   * via decode-and-discard, so the landing is sample-accurate.
   *
   * <p>The input must be positioned at the target frame's read position (see {@link
   * ApeStreamMetadata#getFrameReadPosition(int)}) and {@link #reposition(long)} must have been
   * called accordingly.
   */
  public void seek(long block) throws IOException, ApeDecoderException {
    // IOException surfaces from the read callback: pre-3.93 streams decode the target frame
    // inside Seek, and 3.93+ streams decode-and-discard for within-frame positioning.
    int result = apeSeek(nativeContext, block);
    if (result != 0) {
      throw new ApeDecoderException("Seek failed", result);
    }
  }

  /**
   * Decodes up to {@code maxBlocks} audio blocks into {@code output}.
   *
   * <p>Passing 0 blocks initializes the decoder without producing data; for pre-3.93 streams
   * MACLib decodes the first frame during initialization, so the input must then stand at the
   * first frame.
   *
   * @param output A direct byte buffer to receive the PCM data.
   * @param maxBlocks The maximum number of blocks to decode.
   * @return The number of blocks decoded, possibly 0 at the end of the stream.
   * @throws IOException If the underlying input threw during a read callback.
   * @throws ApeDecoderException If MACLib reported a decode error.
   */
  public int decode(ByteBuffer output, int maxBlocks) throws IOException, ApeDecoderException {
    int result = apeDecode(nativeContext, output, maxBlocks);
    if (result < 0) {
      if (endOfExtractorInput) {
        // Starvation at the physical end of the stream: report a clean truncation error rather
        // than a generic decode failure.
        throw new ApeDecoderException("Input ended before the audio data", -result);
      }
      throw new ApeDecoderException("Decode failed", -result);
    }
    output.position(0);
    output.limit(result * blockAlign());
    return result;
  }

  /** Returns the bytes per audio block reported by the native decoder. */
  public int blockAlign() {
    return apeGetBlockAlign(nativeContext);
  }

  /** Releases the native decoder. */
  public void release() {
    apeRelease(nativeContext);
  }

  private int readFromExtractorInput(
      ExtractorInput extractorInput, byte[] tempBuffer, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }
    int read = extractorInput.read(tempBuffer, offset, length);
    if (read == C.RESULT_END_OF_INPUT) {
      endOfExtractorInput = true;
      read = 0;
    }
    return read;
  }

  private native long apeInit(byte[] prefix, long virtualSize, int threads, int[] errorHolder);

  private native int apeDecode(long context, ByteBuffer output, int maxBlocks) throws IOException;

  private native int apeSeek(long context, long block) throws IOException;

  private native void apeReposition(long context, long virtualPosition);

  private native int apeGetBlockAlign(long context);

  private native void apeRelease(long context);
}
