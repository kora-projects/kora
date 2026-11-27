package io.koraframework.json.common;

import com.alibaba.fastjson2.*;
import io.koraframework.common.annotation.*;
import io.koraframework.common.util.*;
import org.jspecify.annotations.*;
import tools.jackson.core.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;

import static io.koraframework.json.common.JsonPools.*;

/**
 * <b>Русский</b>: Контракт читателя JSON со всеми методами чтения
 * <hr>
 * <b>English</b>: JSON reader contract with all read methods
 */
public interface JsonReader<T> extends Mapping.MappingFunction {

    @Nullable
    default T read(ByteBuffer buffer) {
        if (buffer == null || !buffer.hasRemaining()) {
            return null;
        }
        int remaining = buffer.remaining();
        if (buffer.hasArray()) {
            return read(buffer.array(), buffer.arrayOffset() + buffer.position(), remaining);
        }
        if (remaining > BUFFER_SIZE) {
            try (var jsonReader = JSONReader.of(new ByteBufferInputStream(buffer), StandardCharsets.UTF_8, DEFAULT_READER_CONTEXT)) {
                return read(jsonReader);
            }
        }
        final byte[] pooledBuf = borrowBuffer();
        try {
            buffer.get(pooledBuf, 0, remaining);
            try (var jsonReader = JSONReader.of(pooledBuf, 0, remaining, DEFAULT_READER_CONTEXT)) {
                return read(jsonReader);
            }
        } finally {
            returnBuffer(pooledBuf);
        }
    }

    @Nullable
    default T read(JsonParser parser) {
        throw new UnsupportedOperationException("This reader does not support Jackson JsonParser directly");
    }

    @Nullable
    default T read(JSONReader reader) {
        throw new UnsupportedOperationException("This reader does not support fastjson2 JSONReader directly");
    }

    @Nullable
    default T read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return read(bytes, 0, bytes.length);
    }

    @Nullable
    default T read(byte[] bytes, int offset, int length) {
        if (bytes == null || length <= 0) {
            return null;
        }
        try (var jsonReader = JSONReader.of(bytes, offset, length, DEFAULT_READER_CONTEXT)) {
            return read(jsonReader);
        }
    }

    @Nullable
    default T read(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        try (var jsonReader = JSONReader.of(str, DEFAULT_READER_CONTEXT)) {
            return read(jsonReader);
        }
    }

    @Nullable
    default T read(InputStream is) {
        if (is == null) {
            return null;
        }
        try (var jsonReader = JSONReader.of(is, StandardCharsets.UTF_8, DEFAULT_READER_CONTEXT)) {
            return read(jsonReader);
        }
    }
}
