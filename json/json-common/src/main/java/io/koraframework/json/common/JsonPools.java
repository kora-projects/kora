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
    private static final int PROBE_LIMIT = 8;

    private static final AtomicReferenceArray<byte[]> GLOBAL_BUF_POOL = new AtomicReferenceArray<>(MAX_POOL_SIZE);
//    private static final AtomicInteger BORROW_CURSOR = new AtomicInteger();
//    private static final AtomicInteger RETURN_CURSOR = new AtomicInteger();

    private JsonPools() {}

    public static byte[] borrowBuffer() {
        int start = (int) Thread.currentThread().threadId();
        for (int i = 0; i < PROBE_LIMIT; i++) {
            int idx = (start + i) & MASK;
            byte[] pooled = GLOBAL_BUF_POOL.getAndSet(idx, null);
            if (pooled != null) {
                return pooled;
            }
        }
        return new byte[BUFFER_SIZE];
    }

    public static void returnBuffer(byte @Nullable [] buf) {
        if (buf == null || buf.length != BUFFER_SIZE) {
            return;
        }
        int start = (int) Thread.currentThread().threadId();
        for (int i = 0; i < PROBE_LIMIT; i++) {
            int idx = (start + i) & MASK;
            if (GLOBAL_BUF_POOL.compareAndSet(idx, null, buf)) {
                return;
            }
        }
    }
}
