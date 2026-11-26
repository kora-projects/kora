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
            if (contentLength > 0 && contentLength <= JsonPools.BUFFER_SIZE) {
                final byte[] initialBuf = JsonPools.borrowBuffer();
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
