package io.koraframework.json.jackson.module.http.server;

import com.alibaba.fastjson2.*;
import com.alibaba.fastjson2.reader.*;
import io.koraframework.http.server.common.request.*;
import io.koraframework.http.server.common.response.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.*;

public final class FastJsonHttpServerRequestMapper<T> implements HttpServerRequestMapper<T> {
    private final ObjectReader<T> objectReader;

    @SuppressWarnings("unchecked")
    public FastJsonHttpServerRequestMapper(Type type) {
        this.objectReader = (ObjectReader<T>) JSONFactory.getDefaultObjectReaderProvider().getObjectReader(type);
    }

    @Override
    public T apply(HttpServerRequest request) throws IOException {
        try (var body = request.body()) {
            var fullContent = body.getFullContentIfAvailable();
            if (fullContent != null) {
                return parseFromByteBuffer(fullContent);
            }
            final byte[] initialBuf = JsonPools.borrowBuffer();
            try {
                var result = body.readAllBytes(initialBuf);
                int len = result.length();
                if (len == 0) {
                    return null;
                }
                var reader = JSONReader.of(result.bytes(), 0, len, JsonPools.DEFAULT_READER_CONTEXT);
                return objectReader.readObject(reader, null, null, 0);
            } finally {
                JsonPools.returnBuffer(initialBuf);
            }
        } catch (JSONException e) {
            throw HttpServerResponseException.of(400, e);
        }
    }

    @Nullable
    private T parseFromByteBuffer(ByteBuffer buffer) {
        int remaining = buffer.remaining();
        if (remaining == 0) {
            return null;
        }
        if (buffer.hasArray()) {
            var reader = JSONReader.of(
                buffer.array(),
                buffer.arrayOffset() + buffer.position(),
                remaining,
                JsonPools.DEFAULT_READER_CONTEXT
            );
            return this.objectReader.readObject(reader, null, null, 0);
        }
        final byte[] pooledBuf = JsonPools.borrowBuffer();
        byte[] heapBytes = pooledBuf.length < remaining ? new byte[remaining] : pooledBuf;
        try {
            buffer.get(heapBytes, 0, remaining);
            var reader = JSONReader.of(heapBytes, 0, remaining, JsonPools.DEFAULT_READER_CONTEXT);
            return this.objectReader.readObject(reader, null, null, 0);
        } finally {
            JsonPools.returnBuffer(pooledBuf);
        }
    }
}
