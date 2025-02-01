/*
 * Copyright 2021 The Data Transfer Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.datatransferproject.transfer.photobucket.client.helper;

import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

/**
 *  https://github.com/square/okhttp/issues/3585#issuecomment-327319196
 */
public class InputStreamRequestBody extends RequestBody {
    private final InputStream inputStream;
    private final MediaType contentType;

    public InputStreamRequestBody(MediaType contentType, InputStream inputStream) {
        if (inputStream == null) throw new NullPointerException("inputStream == null");
        this.contentType = contentType;
        this.inputStream = inputStream;
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return contentType;
    }

    @Override
    public long contentLength() throws IOException {
        return -1;
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        try (Source source = Okio.source(inputStream)) {
            sink.writeAll(source);
        }
    }
}
