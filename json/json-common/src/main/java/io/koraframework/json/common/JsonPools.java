package io.koraframework.json.common;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class JsonPools {
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int MAX_POOL_SIZE = 64;

    public static final JSONReader.Context DEFAULT_READER_CONTEXT = new JSONReader.Context(JSONFactory.getDefaultObjectReaderProvider());
    public static final JSONWriter.Context DEFAULT_WRITER_CONTEXT = new JSONWriter.Context(JSONFactory.getDefaultObjectWriterProvider());

    private static final ThreadLocal<byte[]> LOCAL_BUFFER = new ThreadLocal<>();
    private static final ConcurrentLinkedQueue<byte[]> GLOBAL_BUF_POOL = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger GLOBAL_BUF_POOL_SIZE = new AtomicInteger();

    private JsonPools() {}

    public static byte[] borrowBuffer() {
        byte[] local = LOCAL_BUFFER.get();
        if (local != null) {
            LOCAL_BUFFER.set(null);
            return local;
        }
        byte[] pooled = GLOBAL_BUF_POOL.poll();
        if (pooled != null) {
            GLOBAL_BUF_POOL_SIZE.decrementAndGet();
            return pooled;
        }
        return new byte[BUFFER_SIZE];
    }

    public static void returnBuffer(byte[] buf) {
        if (buf == null || buf.length != BUFFER_SIZE) {
            return;
        }
        if (LOCAL_BUFFER.get() == null) {
            LOCAL_BUFFER.set(buf);
            return;
        }
        if (GLOBAL_BUF_POOL_SIZE.get() < MAX_POOL_SIZE) {
            GLOBAL_BUF_POOL_SIZE.incrementAndGet();
            GLOBAL_BUF_POOL.offer(buf);
        }
    }
}
