package io.koraframework.json.jackson.module.http;

import com.alibaba.fastjson2.*;
import com.alibaba.fastjson2.writer.*;
import io.koraframework.http.common.body.*;
import io.koraframework.json.common.*;

import java.io.*;
import java.nio.*;
import java.util.function.*;

public final class FastJsonHttpBodyOutput<T> implements HttpBodyOutput {
    private final ObjectWriter<T> objectWriter;
    private final T value;

    public FastJsonHttpBodyOutput(ObjectWriter<T> objectWriter, T value) {
        this.objectWriter = objectWriter;
        this.value = value;
    }

    @Override
    public long contentLength() {
        return -1;
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public ByteBuffer writeToByteBuffer(IntFunction<ByteBuffer> allocator) {
        try (var writer = JSONWriter.ofUTF8(JsonPools.DEFAULT_WRITER_CONTEXT)) {
            objectWriter.write(writer, value, null, null, 0);
            int size = writer.size();
            var target = allocator.apply(size);
            writer.flushTo(new ByteBufferOutputStream(target));
            target.flip();
            return target;
        } catch (IOException e) {
            throw new JSONException(e.getMessage(), e);
        }
    }

    @Override
    public void write(OutputStream os) throws IOException {
        try (var writer = JSONWriter.ofUTF8(JsonPools.DEFAULT_WRITER_CONTEXT)) {
            objectWriter.write(writer, value, null, null, 0);
            writer.flushTo(os);
        }
    }

    @Override
    public void close() {
    }

    private static final class ByteBufferOutputStream extends OutputStream {
        private final ByteBuffer buffer;

        ByteBufferOutputStream(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public void write(int b) {
            buffer.put((byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            buffer.put(b, off, len);
        }
    }
}
