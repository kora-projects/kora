package io.koraframework.json.common;

import com.alibaba.fastjson2.*;
import org.jctools.queues.*;
import org.jspecify.annotations.*;

public final class JsonPools {

    public static final JSONReader.Context DEFAULT_READER_CONTEXT = new JSONReader.Context(JSONFactory.getDefaultObjectReaderProvider());
    public static final JSONWriter.Context DEFAULT_WRITER_CONTEXT = new JSONWriter.Context(JSONFactory.getDefaultObjectWriterProvider());

    private static final int BUFFER_SIZE = 8 * 1024;
    private static final int MAX_POOL_SIZE = 64;

    private static final MpmcArrayQueue<byte[]> GLOBAL_BUF_POOL = new MpmcArrayQueue<>(MAX_POOL_SIZE);

    private JsonPools() {}

    public static byte[] borrowBuffer() {
        byte[] pooled = GLOBAL_BUF_POOL.poll();
        return pooled != null ? pooled : new byte[BUFFER_SIZE];
    }

    public static void returnBuffer(byte @Nullable [] buf) {
        if (buf != null && buf.length == BUFFER_SIZE) {
            GLOBAL_BUF_POOL.offer(buf);
        }
    }
}
