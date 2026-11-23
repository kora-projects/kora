package io.koraframework.json.jackson.module.http;

import com.alibaba.fastjson2.*;
import com.alibaba.fastjson2.writer.*;
import io.koraframework.http.common.body.*;
import io.koraframework.json.common.*;

import java.io.*;

import static io.koraframework.json.common.JsonWriter.*;

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
    public boolean writeTo(HttpBodySink sink) throws IOException {
        if (value == null) {
            sink.acceptBytes(NULL_BYTES, 0, NULL_BYTES.length, null);
            return true;
        }
        try (var jsonWriter = JSONWriter.ofUTF8(JsonPools.DEFAULT_WRITER_CONTEXT)) {
            objectWriter.write(jsonWriter, value, null, null, 0);
            int size = jsonWriter.size();
            if (size == 0) {
                sink.acceptBytes(HttpBodySink.EMPTY_BYTES, 0, 0, null);
                return true;
            }
            var target = sink.allocateBuffer(size);
            jsonWriter.flushTo(new HttpBodySink.ByteBufferOutputStream(target));
            target.flip();
            sink.acceptAllocatedBuffer(target);
            return true;
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
