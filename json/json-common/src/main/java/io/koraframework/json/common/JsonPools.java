package io.koraframework.json.common;

import com.alibaba.fastjson2.*;
import org.jspecify.annotations.*;

import java.util.concurrent.atomic.*;

public final class JsonPools {

    public static final JSONReader.Context DEFAULT_READER_CONTEXT = new JSONReader.Context(JSONFactory.getDefaultObjectReaderProvider());
    public static final JSONWriter.Context DEFAULT_WRITER_CONTEXT = new JSONWriter.Context(JSONFactory.getDefaultObjectWriterProvider());

    private static final int BUFFER_SIZE = 8 * 1024;
    private static final int MAX_POOL_SIZE = 64;
    private static final int MASK = MAX_POOL_SIZE - 1;

    private static final AtomicReferenceArray<byte[]> GLOBAL_BUF_POOL = new AtomicReferenceArray<>(MAX_POOL_SIZE);
    private static final AtomicInteger CURSOR = new AtomicInteger();

    private JsonPools() {}

    public static byte[] borrowBuffer() {
        int idx = CURSOR.getAndIncrement() & MASK;
        byte[] pooled = GLOBAL_BUF_POOL.getAndSet(idx, null);
        return pooled != null ? pooled : new byte[BUFFER_SIZE];
    }

    public static void returnBuffer(byte @Nullable [] buf) {
        if (buf != null && buf.length == BUFFER_SIZE) {
            int idx = CURSOR.getAndDecrement() & MASK;
            GLOBAL_BUF_POOL.compareAndSet(idx, null, buf);
        }
    }
}
