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

import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.util.LibraryLoader;
import androidx.media3.common.util.UnstableApi;

/** Configures and queries the underlying native library. */
@UnstableApi
public final class ApeLibrary {

  static {
    MediaLibraryInfo.registerModule("media3.decoder.ape");
  }

  private static final LibraryLoader LOADER =
      new LibraryLoader("apeJNI") {
        @Override
        protected void loadLibrary(String name) {
          System.loadLibrary(name);
        }
      };

  private ApeLibrary() {}

  /**
   * Overrides the names of the native libraries. Must be called before any other method of this
   * class or of the other classes in this package.
   */
  public static void setLibraries(String... libraries) {
    LOADER.setLibraries(libraries);
  }

  /** Returns whether the underlying library is available, loading it if necessary. */
  public static boolean isAvailable() {
    return LOADER.isAvailable();
  }

  /**
   * Returns the MACLib version the native library was built from, e.g. "13.27", or null if the
   * native library is not available.
   */
  public static @androidx.annotation.Nullable String getMacLibVersion() {
    if (!isAvailable()) {
      return null;
    }
    int version = apeGetMacLibVersion();
    return (version / 100) + "." + String.format(java.util.Locale.ROOT, "%02d", version % 100);
  }

  private static native int apeGetMacLibVersion();
}
