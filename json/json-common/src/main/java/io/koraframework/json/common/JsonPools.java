package io.koraframework.json.common;

import com.alibaba.fastjson2.*;
import org.jspecify.annotations.*;

import java.lang.ref.*;
import java.util.concurrent.atomic.*;

public final class JsonPools {
    public static final JSONReader.Context DEFAULT_READER_CONTEXT = new JSONReader.Context(JSONFactory.getDefaultObjectReaderProvider());
    public static final JSONWriter.Context DEFAULT_WRITER_CONTEXT = new JSONWriter.Context(JSONFactory.getDefaultObjectWriterProvider());

    public static final int BUFFER_SIZE = 32 * 1024;
    private static final int MAX_POOL_SIZE = 64;
    private static final int MASK = MAX_POOL_SIZE - 1;
    private static final int PROBE_LIMIT = 8;

    private static final AtomicReferenceArray<SoftReference<byte[]>> GLOBAL_BUF_POOL = new AtomicReferenceArray<>(MAX_POOL_SIZE);

    private JsonPools() {}

    public static byte[] borrowBuffer() {
        int start = (int) Thread.currentThread().threadId();
        for (int i = 0; i < PROBE_LIMIT; i++) {
            int idx = (start + i) & MASK;
            SoftReference<byte[]> ref = GLOBAL_BUF_POOL.getAndSet(idx, null);
            if (ref != null) {
                byte[] pooled = ref.get();
                if (pooled != null) {
                    return pooled;
                }
            }
        }
        return new byte[BUFFER_SIZE];
    }

    public static void returnBuffer(byte @Nullable [] buf) {
        if (buf == null || buf.length != BUFFER_SIZE) {
            return;
        }
        int start = (int) Thread.currentThread().threadId();
        SoftReference<byte[]> ref = null;
        for (int i = 0; i < PROBE_LIMIT; i++) {
            int idx = (start + i) & MASK;
            SoftReference<byte[]> cur = GLOBAL_BUF_POOL.get(idx);
            if (cur == null || cur.refersTo(null)) {
                if (ref == null) {
                    ref = new SoftReference<>(buf);
                }
                if (GLOBAL_BUF_POOL.compareAndSet(idx, cur, ref)) {
                    return;
                }
            }
        }
    }
}
