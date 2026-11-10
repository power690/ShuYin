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

import android.net.Uri;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.text.SubtitleParser;
import java.util.List;
import java.util.Map;

/**
 * The factory behind {@link ApeSupport}: {@link ApeExtractor} first, then whatever the wrapped
 * factory provides. Every other {@link ExtractorsFactory} method delegates to the wrapped factory;
 * the setters keep whatever instance the wrapped factory returns, so both mutable factories
 * (which return {@code this}) and immutable ones (which return replacements) stay wired in.
 */
@UnstableApi
/* package */ final class ApeExtractorsFactory implements ExtractorsFactory {

  private ExtractorsFactory base;

  /* package */ ApeExtractorsFactory(ExtractorsFactory base) {
    this.base = base;
  }

  @Override
  public Extractor[] createExtractors() {
    return prepend(base.createExtractors());
  }

  @Override
  public Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders) {
    return prepend(base.createExtractors(uri, responseHeaders));
  }

  @Override
  public ExtractorsFactory setSubtitleParserFactory(
      SubtitleParser.Factory subtitleParserFactory) {
    base = base.setSubtitleParserFactory(subtitleParserFactory);
    return this;
  }

  @SuppressWarnings("deprecation") // Delegating the interface's own deprecated default.
  @Override
  public ExtractorsFactory experimentalSetTextTrackTranscodingEnabled(
      boolean textTrackTranscodingEnabled) {
    base = base.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled);
    return this;
  }

  private static Extractor[] prepend(Extractor[] extractors) {
    Extractor[] result = new Extractor[extractors.length + 1];
    result[0] = new ApeExtractor();
    System.arraycopy(extractors, 0, result, 1, extractors.length);
    return result;
  }
}
