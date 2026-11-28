package io.koraframework.http.common.body;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.io.*;

public final class FastJsonHttpBodyOutput<T> implements HttpBodyOutput {
    private static final byte[] NULL_BYTES = new byte[] {'n', 'u', 'l', 'l'};
    private static final int MAX_SINGLE_BUFFER_SIZE = JsonPools.LARGE_BUFFER_SIZE;

    private final JsonWriter<T> writer;
    @Nullable
    private final T value;
    @Nullable
    private JSONWriter preparedWriter;

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
        var jsonWriter = JSONWriter.ofUTF8(JsonPools.DEFAULT_WRITER_CONTEXT);
        boolean keepWriter = false;
        try {
            writer.write(jsonWriter, value);
            int size = jsonWriter.size();
            if (size == 0) {
                sink.acceptBytes(HttpBodySink.EMPTY_BYTES, 0, 0, null);
                return true;
            }
            if (size > MAX_SINGLE_BUFFER_SIZE) {
                this.preparedWriter = jsonWriter;
                keepWriter = true;
                return false;
            }
            var buffer = sink.allocateBuffer(size);
            jsonWriter.flushTo(new HttpBodySink.ByteBufferOutputStream(buffer));
            buffer.flip();
            sink.acceptAllocatedBuffer(buffer);
            return true;
        } finally {
            if (!keepWriter) {
                jsonWriter.close();
            }
        }
    }

    @Override
    public void write(OutputStream os) throws IOException {
        var ready = this.preparedWriter;
        if (ready != null) {
            this.preparedWriter = null;
            try (ready) {
                ready.flushTo(os);
            }
            return;
        }
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
        var ready = this.preparedWriter;
        if (ready != null) {
            this.preparedWriter = null;
            ready.close();
        }
    }
}
