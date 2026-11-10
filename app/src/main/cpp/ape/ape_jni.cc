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
#include <android/log.h>
#include <jni.h>

#include <cstdint>
#include <cstring>
#include <string>

#include "All.h"
#include "MACLib.h"
#include "APEInfo.h"
#include "APETag.h"
#include "Version.h"
#include "ape_io_bridge.h"

#define LOG_TAG "ape_jni"
#define ALOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

// Bound via RegisterNatives in JNI_OnLoad below; the Java package name appears in exactly one
// place (kPackagePath), so a package rename that misses the native side fails loudly at library
// load instead of as an UnsatisfiedLinkError at first use.
#define DECODER_FUNC(RETURN_TYPE, NAME, ...) \
  static RETURN_TYPE NAME(JNIEnv* env, jobject thiz, ##__VA_ARGS__)

namespace {

// Returned (negated) when MACLib raised a C++ exception instead of an error
// code; MACLib throws on bitstream starvation (UnBitArrayBase.cpp).
const int kErrorNativeException = 9000;

/**
 * A tag that reports "analyzed, zero bytes" without any IO. Keeps MACLib away
 * from the file tail entirely: the Java side owns metadata, and the virtual
 * file size already excludes the tag region.
 */
class NoTag : public APE::CAPETag {
 public:
  explicit NoTag(APE::IAPEIO* io) : APE::CAPETag(io, /* bAnalyze= */ false) {}
  int GetTagBytes() override { return 0; }
  bool GetAnalyzed() override { return true; }
};

struct ApeDecoderContext {
  JavaStreamIO* io;                     // Owned; shared with info by pointer.
  APE::IAPEDecompress* decompress;      // Owned; deleting it deletes the info.
  int block_align;

  ApeDecoderContext() : io(nullptr), decompress(nullptr), block_align(0) {}

  ~ApeDecoderContext() {
    delete decompress;
    delete io;
  }
};

ApeDecoderContext* GetContext(jlong context) {
  // The context travels as a zero-extended uintptr_t so that 32-bit heap
  // addresses with the top bit set do not turn into negative jlongs (the
  // negative range is reserved for error codes).
  return reinterpret_cast<ApeDecoderContext*>(static_cast<uintptr_t>(context));
}

}  // namespace

DECODER_FUNC(jlong, apeInit, jbyteArray jprefix, jlong virtual_size, jint threads,
             jintArray jerror) {
  // The context pointer is returned as-is: on Android arm64 heap pointers
  // carry a top-byte tag, so no value range is available for error codes.
  // Failures return 0 and report the MACLib error through jerror[0].
  jint error_out = ERROR_SUCCESS;
  jsize prefix_size = env->GetArrayLength(jprefix);
  jbyte* prefix = env->GetByteArrayElements(jprefix, nullptr);
  if (prefix == nullptr) {
    error_out = ERROR_INSUFFICIENT_MEMORY;
    env->SetIntArrayRegion(jerror, 0, 1, &error_out);
    return 0;
  }

  ApeDecoderContext* context = new ApeDecoderContext();
  context->io = new JavaStreamIO(reinterpret_cast<const uint8_t*>(prefix),
                                 static_cast<size_t>(prefix_size), virtual_size);
  env->ReleaseByteArrayElements(jprefix, prefix, JNI_ABORT);
  context->io->SetEnv(env, thiz);

  int error_code = ERROR_SUCCESS;
  try {
    // Analyzing the container needs only the prefix, which the IO serves from
    // memory; no callback into Java happens here.
    APE::CAPEInfo* info =
        new APE::CAPEInfo(&error_code, context->io, new NoTag(context->io));
    if (error_code != ERROR_SUCCESS) {
      delete info;
    } else {
      context->decompress =
          CreateIAPEDecompressEx2(info, /* nStartBlock= */ -1, /* nFinishBlock= */ -1,
                                  &error_code);
    }
  } catch (...) {
    error_code = kErrorNativeException;
  }

  if (error_code != ERROR_SUCCESS || context->decompress == nullptr) {
    if (error_code == ERROR_SUCCESS) {
      error_code = ERROR_UNDEFINED;
    }
    ALOGE("apeInit failed with MACLib error %d", error_code);
    delete context;
    error_out = error_code;
    env->SetIntArrayRegion(jerror, 0, 1, &error_out);
    return 0;
  }

  context->block_align =
      static_cast<int>(context->decompress->GetInfo(APE::IAPEDecompress::APE_INFO_BLOCK_ALIGN));
  if (threads > 1) {
    // Must happen before the first GetData/Seek: the worker pool is sized at
    // decompressor initialization. Workers decode independent frames; the
    // frame reads still happen on the calling thread.
    context->decompress->SetNumberOfThreads(threads);
  }
  env->SetIntArrayRegion(jerror, 0, 1, &error_out);
  return static_cast<jlong>(reinterpret_cast<uintptr_t>(context));
}

DECODER_FUNC(jint, apeDecode, jlong jcontext, jobject joutput, jint max_blocks) {
  ApeDecoderContext* context = GetContext(jcontext);
  context->io->SetEnv(env, thiz);

  unsigned char* output =
      static_cast<unsigned char*>(env->GetDirectBufferAddress(joutput));
  if (output == nullptr) {
    return -ERROR_BAD_PARAMETER;
  }
  jlong capacity = env->GetDirectBufferCapacity(joutput);
  if (max_blocks > 0 && capacity < static_cast<jlong>(max_blocks) * context->block_align) {
    return -ERROR_BAD_PARAMETER;
  }

  // Playback-oriented processing: undo the archival-form transforms so the
  // output is little-endian PCM (float, signed-8-bit and big-endian streams
  // reproduce the original file bytes otherwise).
  APE::IAPEDecompress::APE_GET_DATA_PROCESSING processing;
  processing.bApplyFloatProcessing = true;
  processing.bApplySigned8BitProcessing = true;
  processing.bApplyBigEndianProcessing = true;

  APE::int64 blocks_retrieved = 0;
  int result;
  try {
    result = context->decompress->GetData(output, max_blocks, &blocks_retrieved, &processing);
  } catch (...) {
    result = kErrorNativeException;
  }
  if (context->io->HasPendingException()) {
    // The pending Java exception is rethrown as soon as this method returns.
    return -ERROR_IO_READ;
  }
  if (result != ERROR_SUCCESS) {
    ALOGE("apeDecode failed with MACLib error %d", result);
    return -result;
  }
  return static_cast<jint>(blocks_retrieved);
}

DECODER_FUNC(jint, apeSeek, jlong jcontext, jlong block) {
  ApeDecoderContext* context = GetContext(jcontext);
  context->io->SetEnv(env, thiz);

  int result;
  try {
    // The pre-3.93 decoder decodes the target frame inside Seek, so this can
    // trigger read callbacks.
    result = context->decompress->Seek(block);
  } catch (...) {
    result = kErrorNativeException;
  }
  if (context->io->HasPendingException()) {
    return ERROR_IO_READ;
  }
  if (result != ERROR_SUCCESS) {
    ALOGE("apeSeek(%lld) failed with MACLib error %d", static_cast<long long>(block), result);
  }
  return result;
}

DECODER_FUNC(void, apeReposition, jlong jcontext, jlong virtual_position) {
  GetContext(jcontext)->io->Reposition(virtual_position);
}

DECODER_FUNC(jint, apeGetBlockAlign, jlong jcontext) {
  return GetContext(jcontext)->block_align;
}

DECODER_FUNC(void, apeRelease, jlong jcontext) {
  delete GetContext(jcontext);
}

static jint apeGetMacLibVersion(JNIEnv*, jclass) {
  // From the pinned SDK's Version.h, e.g. 13 * 100 + 20 -> "13.27".
  return APE_VERSION_MAJOR * 100 + APE_VERSION_REVISION;
}

// The single place the Java package name exists on the native side.
static const char kPackagePath[] = "io/github/eugenedibtsev/media3/ape/";

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
  JNIEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return JNI_ERR;
  }

  static const JNINativeMethod decoder_methods[] = {
      {"apeInit", "([BJI[I)J", reinterpret_cast<void*>(apeInit)},
      {"apeDecode", "(JLjava/nio/ByteBuffer;I)I", reinterpret_cast<void*>(apeDecode)},
      {"apeSeek", "(JJ)I", reinterpret_cast<void*>(apeSeek)},
      {"apeReposition", "(JJ)V", reinterpret_cast<void*>(apeReposition)},
      {"apeGetBlockAlign", "(J)I", reinterpret_cast<void*>(apeGetBlockAlign)},
      {"apeRelease", "(J)V", reinterpret_cast<void*>(apeRelease)},
  };
  static const JNINativeMethod library_methods[] = {
      {"apeGetMacLibVersion", "()I", reinterpret_cast<void*>(apeGetMacLibVersion)},
  };

  const std::string package(kPackagePath);
  jclass decoder_class = env->FindClass((package + "ApeDecoderJni").c_str());
  if (decoder_class == nullptr
      || env->RegisterNatives(decoder_class, decoder_methods,
                              sizeof(decoder_methods) / sizeof(decoder_methods[0])) != JNI_OK) {
    ALOGE("RegisterNatives failed for %sApeDecoderJni", kPackagePath);
    return JNI_ERR;
  }
  jclass library_class = env->FindClass((package + "ApeLibrary").c_str());
  if (library_class == nullptr
      || env->RegisterNatives(library_class, library_methods,
                              sizeof(library_methods) / sizeof(library_methods[0])) != JNI_OK) {
    ALOGE("RegisterNatives failed for %sApeLibrary", kPackagePath);
    return JNI_ERR;
  }
  return JNI_VERSION_1_6;
}
