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

import androidx.media3.common.util.UnstableApi;

/** Thrown when a native APE decoder error occurs. */
@UnstableApi
public final class ApeDecoderException extends Exception {

  /**
   * The MACLib error code that caused the failure, e.g. 1009 for an invalid checksum, or 0 if the
   * failure did not originate from a MACLib return code.
   */
  public final int errorCode;

  /* package */ ApeDecoderException(String message) {
    super(message);
    this.errorCode = 0;
  }

  /* package */ ApeDecoderException(String message, int errorCode) {
    super(message + " (MACLib error " + errorCode + ")");
    this.errorCode = errorCode;
  }
}
