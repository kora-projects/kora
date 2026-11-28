package io.koraframework.http.server.common.request.mapper;

import com.alibaba.fastjson2.*;
import io.koraframework.http.server.common.request.*;
import io.koraframework.http.server.common.response.*;
import io.koraframework.json.common.*;

import java.io.*;

public final class JsonReaderHttpServerRequestMapper<T> implements HttpServerRequestMapper<T> {

    private final JsonReader<T> reader;

    public JsonReaderHttpServerRequestMapper(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public T apply(HttpServerRequest request) throws IOException {
        try (var body = request.body()) {
            long contentLength = body.contentLength();
            if (contentLength == 0) {
                return null;
            }
            if (contentLength > 0 && contentLength <= 512) {
                var fullContent = body.getFullContentIfAvailable();
                if (fullContent != null && fullContent.hasArray()) {
                    int remaining = fullContent.remaining();
                    return remaining > 0
                        ? reader.read(fullContent.array(), fullContent.arrayOffset() + fullContent.position(), remaining)
                        : null;
                }
            }
            if (contentLength > 0 && contentLength <= JsonPools.LARGE_BUFFER_SIZE) {
                final byte[] initialBuf = contentLength <= JsonPools.BUFFER_SIZE
                    ? JsonPools.borrowBuffer()
                    : JsonPools.borrowLargeBuffer();
                try {
                    var result = body.readAllBytes(initialBuf);
                    if (result.length() == 0) {
                        return null;
                    }
                    return reader.read(result.bytes(), 0, result.length());
                } finally {
                    JsonPools.returnBuffer(initialBuf);
                }
            }
            try (var is = body.asInputStream()) {
                return reader.read(is);
            }
        } catch (JSONException e) {
            throw HttpServerResponseException.of(400, e);
        }
    }
}
