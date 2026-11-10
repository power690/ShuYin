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
#ifndef MEDIA3_DECODER_APE_IO_BRIDGE_H_
#define MEDIA3_DECODER_APE_IO_BRIDGE_H_

#include <jni.h>

#include <cstdint>
#include <vector>

#include "All.h"
#include "IAPEIO.h"

/**
 * IAPEIO over a Media3 ExtractorInput reached through a JNI callback.
 *
 * The virtual file is addressed relative to the 'MAC ' magic and its size
 * excludes trailing tags. Its head (the container prefix: headers, stored WAV
 * header, seek tables) is served from a memory copy; everything after that is
 * pulled forward from the Java input. A bounded ring of recently delivered
 * bytes serves the small backward reads MACLib performs when re-anchoring a
 * frame on the 32-bit word grid (at most a few bytes in practice).
 *
 * The JNIEnv/object pair is only valid during a native call and must be set
 * on entry to every JNI method that can trigger IO. All IO happens on the
 * calling thread: MACLib reads frame bytes before handing them to its worker,
 * so the callback never runs on a MACLib-internal thread.
 */
class JavaStreamIO : public APE::IAPEIO {
 public:
  JavaStreamIO(const uint8_t* prefix, size_t prefix_size, int64_t virtual_size);
  ~JavaStreamIO() override;

  /** Binds the callback target for the duration of the current native call. */
  void SetEnv(JNIEnv* env, jobject callback_object);

  /** The stream was repositioned externally; the rewind ring is invalid. */
  void Reposition(int64_t position);

  /** Whether a Java exception is pending from a callback. */
  bool HasPendingException() const { return pending_exception_; }

  // IAPEIO implementation.
  int Open(const APE::str_utfn* name, bool open_read_only) override;
  int Close() override;
  int Read(void* buffer, APE::int64 bytes_to_read, APE::int64* bytes_read) override;
  int Write(const void* buffer, APE::int64 bytes_to_write, APE::int64* bytes_written) override;
  int Seek(APE::int64 position, APE::SeekMethod method) override;
  int Create(const APE::str_utfn* name) override;
  int Delete() override;
  int SetEOF() override;
  unsigned char* GetBuffer(int* buffer_bytes) override;
  APE::int64 GetPosition() override;
  APE::int64 GetSize() override;

 private:
  // Sized from measurement, not guesswork: over the whole corpus (50 files,
  // 100 random seeks each) the largest backward read MACLib ever asked for was
  // 7 bytes, so this leaves a 36x margin. Keeping it below the read size also
  // means AppendToRing always takes its memcpy-only path; a larger ring would
  // slide the retained window with a memmove on every single read.
  static const int64_t kRingSize = 256;

  // Pulls up to `size` bytes from the Java input at the stream head.
  // Returns the number of bytes delivered, 0 at the end of the input or -1
  // on failure (a Java exception is then pending).
  int64_t PullFromJava(uint8_t* target, int64_t size);

  // Appends delivered bytes to the rewind ring.
  void AppendToRing(const uint8_t* data, int64_t size);

  std::vector<uint8_t> prefix_;
  int64_t virtual_size_;
  int64_t position_;  // MACLib's cursor.
  int64_t head_;      // Furthest byte delivered from the Java input.

  uint8_t ring_[kRingSize];
  int64_t ring_fill_;  // The ring holds [head_ - ring_fill_, head_).

  JNIEnv* env_;
  jobject callback_object_;
  jmethodID read_method_;
  bool pending_exception_;
};

#endif  // MEDIA3_DECODER_APE_IO_BRIDGE_H_
