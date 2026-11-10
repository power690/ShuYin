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
#include "ape_io_bridge.h"

#include <android/log.h>

#include <cstring>

namespace {

int64_t MinInt64(int64_t a, int64_t b) { return a < b ? a : b; }

}  // namespace

JavaStreamIO::JavaStreamIO(const uint8_t* prefix, size_t prefix_size, int64_t virtual_size)
    : prefix_(prefix, prefix + prefix_size),
      virtual_size_(virtual_size),
      position_(0),
      head_(0),
      ring_fill_(0),
      env_(nullptr),
      callback_object_(nullptr),
      read_method_(nullptr),
      pending_exception_(false) {}

JavaStreamIO::~JavaStreamIO() {}

void JavaStreamIO::SetEnv(JNIEnv* env, jobject callback_object) {
  env_ = env;
  callback_object_ = callback_object;
  pending_exception_ = false;
  if (read_method_ == nullptr) {
    jclass cls = env->GetObjectClass(callback_object);
    read_method_ = env->GetMethodID(cls, "read", "(Ljava/nio/ByteBuffer;)I");
    env->DeleteLocalRef(cls);
  }
}

void JavaStreamIO::Reposition(int64_t position) {
  head_ = position;
  ring_fill_ = 0;
  // Deliberately NOT syncing position_ here. MACLib's cursor stays authoritative: when the next
  // frame is consecutive, SeekToFrame skips its io->Seek and continues reading mid-buffer, so
  // forcing the cursor to the frame start makes the next fill re-serve bytes the bit array
  // already consumed -- observed as checksum errors (1009) on the legacy seek matrix. A stale
  // cursor ahead of the new head only costs a forward-skip re-pull, which is correct if slower.
}

int64_t JavaStreamIO::PullFromJava(uint8_t* target, int64_t size) {
  if (pending_exception_) {
    return -1;
  }
  jobject byte_buffer = env_->NewDirectByteBuffer(target, size);
  jint result = env_->CallIntMethod(callback_object_, read_method_, byte_buffer);
  if (env_->ExceptionCheck()) {
    // The exception stays pending and is rethrown in Java when the outer
    // native method returns; no further JNI calls are allowed until then.
    pending_exception_ = true;
    result = -1;
  }
  env_->DeleteLocalRef(byte_buffer);
  if (result > 0) {
    AppendToRing(target, result);
    head_ += result;
  }
  return result;
}

void JavaStreamIO::AppendToRing(const uint8_t* data, int64_t size) {
  if (size >= kRingSize) {
    memcpy(ring_, data + size - kRingSize, kRingSize);
    ring_fill_ = kRingSize;
    return;
  }
  const int64_t keep = MinInt64(ring_fill_, kRingSize - size);
  if (keep > 0) {
    memmove(ring_, ring_ + ring_fill_ - keep, keep);
  }
  memcpy(ring_ + keep, data, size);
  ring_fill_ = keep + size;
}

int JavaStreamIO::Open(const APE::str_utfn* /* name */, bool /* open_read_only */) {
  return ERROR_SUCCESS;
}

int JavaStreamIO::Close() { return ERROR_SUCCESS; }

int JavaStreamIO::Read(void* buffer, APE::int64 bytes_to_read, APE::int64* bytes_read) {
  uint8_t* out = static_cast<uint8_t*>(buffer);
  int64_t served = 0;

  while (served < bytes_to_read && position_ < virtual_size_) {
    const int64_t remaining = bytes_to_read - served;
    int64_t chunk = 0;

    if (position_ < static_cast<int64_t>(prefix_.size())) {
      // Container head, served from memory (this also covers the repeated
      // header reads MACLib performs while analyzing the stream).
      chunk = MinInt64(remaining, static_cast<int64_t>(prefix_.size()) - position_);
      memcpy(out + served, prefix_.data() + position_, chunk);
    } else if (position_ < head_) {
      const int64_t rewind = head_ - position_;
      if (rewind > ring_fill_) {
        // Beyond what the ring retained: the access contract is broken.
        // Fail the read; MACLib turns this into a decode error.
        __android_log_print(ANDROID_LOG_ERROR, "ape_io_bridge",
                            "rewind %lld exceeds ring fill %lld (position=%lld head=%lld)",
                            static_cast<long long>(rewind), static_cast<long long>(ring_fill_),
                            static_cast<long long>(position_), static_cast<long long>(head_));
        break;
      }
      chunk = MinInt64(remaining, rewind);
      memcpy(out + served, ring_ + ring_fill_ - rewind, chunk);
    } else {
      if (position_ > head_) {
        // Forward gap: pull and discard until the head catches up.
        uint8_t scratch[8192];
        while (head_ < position_) {
          const int64_t pulled =
              PullFromJava(scratch, MinInt64(position_ - head_, static_cast<int64_t>(sizeof(scratch))));
          if (pulled <= 0) {
            break;
          }
        }
        if (head_ < position_) {
          break;
        }
      }
      const int64_t pulled =
          PullFromJava(out + served, MinInt64(remaining, virtual_size_ - position_));
      if (pulled <= 0) {
        break;
      }
      chunk = pulled;
    }

    served += chunk;
    position_ += chunk;
  }

  if (bytes_read != APE_NULL) {
    *bytes_read = served;
  }
  if (served >= bytes_to_read) {
    return ERROR_SUCCESS;
  }
  // A short read only means "end of file" when the virtual file really has run out; MACLib asks
  // for up to four bytes past the last frame and copes with getting fewer. Any other shortfall is
  // the input giving up mid-frame -- a cancelled load, an interrupted thread, a network stall --
  // and reporting it as success would let MACLib conclude the file is truncated (error 1008) and
  // fail playback for good. Calling it an IO error instead keeps it retryable.
  return (position_ >= virtual_size_) ? ERROR_SUCCESS : ERROR_IO_READ;
}

int JavaStreamIO::Write(const void* /* buffer */, APE::int64 /* bytes_to_write */,
                        APE::int64* /* bytes_written */) {
  return ERROR_IO_WRITE;
}

int JavaStreamIO::Seek(APE::int64 position, APE::SeekMethod method) {
  if (method == APE::SeekFileBegin) {
    position_ = position;
  } else if (method == APE::SeekFileCurrent) {
    position_ += position;
  } else {
    // MACLib passes tail offsets as -abs(position); mirror CStdLibFileIO.
    position_ = virtual_size_ - ((position < 0) ? -position : position);
  }
  if (position_ < 0) {
    position_ = 0;
  }
  if (position_ > virtual_size_) {
    position_ = virtual_size_;
  }
  return ERROR_SUCCESS;
}

int JavaStreamIO::Create(const APE::str_utfn* /* name */) { return ERROR_UNDEFINED; }

int JavaStreamIO::Delete() { return ERROR_UNDEFINED; }

int JavaStreamIO::SetEOF() { return ERROR_UNDEFINED; }

unsigned char* JavaStreamIO::GetBuffer(int* /* buffer_bytes */) { return APE_NULL; }

APE::int64 JavaStreamIO::GetPosition() { return position_; }

APE::int64 JavaStreamIO::GetSize() { return virtual_size_; }
