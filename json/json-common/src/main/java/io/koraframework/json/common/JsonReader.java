package io.koraframework.json.common;

import com.alibaba.fastjson2.*;
import io.koraframework.common.annotation.*;
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
        final byte[] pooledBuf = JsonPools.borrowBuffer();
        byte[] buf = pooledBuf;
        try {
            if (remaining > buf.length) {
                buf = new byte[remaining];
            }
            buffer.get(buf, 0, remaining);
            return read(JSONReader.of(buf, 0, remaining, DEFAULT_READER_CONTEXT));
        } finally {
            JsonPools.returnBuffer(pooledBuf);
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
        return read(JSONReader.of(bytes, offset, length, DEFAULT_READER_CONTEXT));
    }

    @Nullable
    default T read(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        return read(JSONReader.of(str, DEFAULT_READER_CONTEXT));
    }

    @Nullable
    default T read(InputStream is) {
        if (is == null) {
            return null;
        }
        final byte[] pooledBuf = JsonPools.borrowBuffer();
        byte[] buf = pooledBuf;
        try {
            int offset = 0;
            while (true) {
                int read = is.readNBytes(buf, offset, buf.length - offset);
                offset += read;
                if (offset < buf.length) {
                    break;
                }
                var newBuf = new byte[buf.length * 2];
                System.arraycopy(buf, 0, newBuf, 0, offset);
                buf = newBuf;
            }
            if (offset == 0) {
                return null;
            }
            return read(JSONReader.of(buf, 0, offset, DEFAULT_READER_CONTEXT));
        } catch (IOException e) {
            throw new JSONException("Failed to read JSON from InputStream", e);
        } finally {
            JsonPools.returnBuffer(pooledBuf);
        }
    }
}
