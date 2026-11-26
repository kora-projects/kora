package io.koraframework.http.common.body;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.io.*;

public final class FastJsonHttpBodyOutput<T> implements HttpBodyOutput {
    private static final byte[] NULL_BYTES = new byte[] {'n', 'u', 'l', 'l'};

    private final JsonWriter<T> writer;
    @Nullable
    private final T value;

    public FastJsonHttpBodyOutput(JsonWriter<T> writer, @Nullable T value) {
        this.writer = writer;
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
    public boolean writeTo(HttpBodySink sink) throws IOException {
        if (value == null) {
            sink.acceptBytes(NULL_BYTES, 0, NULL_BYTES.length, null);
            return true;
        }
        try (var jsonWriter = JSONWriter.ofUTF8(JsonPools.DEFAULT_WRITER_CONTEXT)) {
            writer.write(jsonWriter, value);
            int size = jsonWriter.size();
            if (size == 0) {
                sink.acceptBytes(HttpBodySink.EMPTY_BYTES, 0, 0, null);
                return true;
            }
            var buffer = sink.allocateBuffer(size);
            jsonWriter.flushTo(new HttpBodySink.ByteBufferOutputStream(buffer));
            buffer.flip();
            sink.acceptAllocatedBuffer(buffer);
            return true;
        }
    }

    @Override
    public void write(OutputStream os) throws IOException {
        if (value == null) {
            os.write(NULL_BYTES);
            return;
        }
        try (var jsonWriter = JSONWriter.ofUTF8(JsonPools.DEFAULT_WRITER_CONTEXT)) {
            writer.write(jsonWriter, value);
            jsonWriter.flushTo(os);
        }
    }

    @Override
    public void close() {
    }
}
