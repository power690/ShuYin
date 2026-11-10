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
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;

/**
 * The one-call entry point: an {@link ExtractorsFactory} with APE support in front.
 *
 * <pre>{@code
 * ExoPlayer player = new ExoPlayer.Builder(context)
 *     .setMediaSourceFactory(
 *         new DefaultMediaSourceFactory(context, ApeSupport.extractorsFactory()))
 *     .build();
 * }</pre>
 *
 * <p>Decoding happens inside the extractor, which emits raw PCM — the standard Media3 renderers
 * play it, and no custom {@code RenderersFactory} is involved.
 *
 * <p>Only Media3 types appear in these signatures, which keeps language bindings (for example
 * .NET for Android) down to this single class.
 */
@UnstableApi
public final class ApeSupport {

  private ApeSupport() {}

  /**
   * Returns an {@link ExtractorsFactory} that tries the APE extractor first and otherwise behaves
   * like {@link DefaultExtractorsFactory}.
   */
  public static ExtractorsFactory extractorsFactory() {
    return extractorsFactory(new DefaultExtractorsFactory());
  }

  /**
   * Returns an {@link ExtractorsFactory} that tries the APE extractor first and then everything
   * {@code base} provides, in {@code base}'s order.
   *
   * <p>APE goes first so that no sniffing extractor in {@code base} claims the stream before the
   * APE extractor sees it. All other {@link ExtractorsFactory} methods delegate to {@code base}.
   */
  public static ExtractorsFactory extractorsFactory(ExtractorsFactory base) {
    return new ApeExtractorsFactory(base);
  }
}
