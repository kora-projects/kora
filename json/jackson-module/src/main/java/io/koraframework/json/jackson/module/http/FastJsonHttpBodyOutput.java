package io.koraframework.json.jackson.module.http;

import com.alibaba.fastjson2.*;
import com.alibaba.fastjson2.writer.*;
import io.koraframework.http.common.body.*;
import io.koraframework.json.common.*;

import java.io.*;

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
    public boolean writeTo(HttpBodySink sink) {
        try (var writer = JSONWriter.ofUTF8(JsonPools.DEFAULT_WRITER_CONTEXT)) {
            objectWriter.write(writer, value, null, null, 0);
            int size = writer.size();
            if (size == 0) {
                sink.acceptBytes(HttpBodySink.EMPTY_BYTES, 0, 0, null);
                return true;
            }
            byte[] pooled = JsonPools.borrowBuffer();
            if (size <= pooled.length) {
                try {
                    writer.flushTo(new HttpBodySink.FixedByteArrayOutputStream(pooled));
                    sink.acceptBytes(pooled, 0, size, () -> JsonPools.returnBuffer(pooled));
                    return true;
                } catch (Throwable t) {
                    JsonPools.returnBuffer(pooled);
                    throw t;
                }
            } else {
                JsonPools.returnBuffer(pooled);
                var target = sink.allocateBuffer(size);
                writer.flushTo(new HttpBodySink.ByteBufferOutputStream(target));
                target.flip();
                sink.acceptAllocatedBuffer(target);
                return true;
            }
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
}
