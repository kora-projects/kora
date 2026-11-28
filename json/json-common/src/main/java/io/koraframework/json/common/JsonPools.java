package io.koraframework.json.common;

import com.alibaba.fastjson2.*;
import org.jspecify.annotations.*;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class JsonPools {
    public static final JSONReader.Context DEFAULT_READER_CONTEXT = new JSONReader.Context(
        JSONReader.Feature.DisableReferenceDetect,
        JSONReader.Feature.DisableSingleQuote,
        JSONReader.Feature.DisableStringArrayUnwrapping
    ).setBufferSize(64 * 1024);
    public static final JSONWriter.Context DEFAULT_WRITER_CONTEXT = new JSONWriter.Context(JSONWriter.Feature.OptimizedForAscii);

    static {
        DEFAULT_READER_CONTEXT.setDateFormat("iso8601");
        DEFAULT_WRITER_CONTEXT.setDateFormat("iso8601");
    }

    public static final int BUFFER_SIZE = 32 * 1024;
    public static final int LARGE_BUFFER_SIZE = 128 * 1024;

    private static final int MAX_POOL_SIZE = 128;
    private static final int MAX_LARGE_POOL_SIZE = 256;
    private static final int MASK = MAX_POOL_SIZE - 1;
    private static final int LARGE_MASK = MAX_LARGE_POOL_SIZE - 1;
    private static final int PROBE_LIMIT = 16;

    private static final AtomicReferenceArray<byte[]> GLOBAL_BUF_POOL = new AtomicReferenceArray<>(MAX_POOL_SIZE);
    private static final AtomicReferenceArray<byte[]> LARGE_BUF_POOL = new AtomicReferenceArray<>(MAX_LARGE_POOL_SIZE);

    private JsonPools() {}

    public static byte[] borrowBuffer() {
        return borrowFrom(GLOBAL_BUF_POOL, MASK, BUFFER_SIZE);
    }

    public static byte[] borrowLargeBuffer() {
        return borrowFrom(LARGE_BUF_POOL, LARGE_MASK, LARGE_BUFFER_SIZE);
    }

    public static void returnBuffer(byte @Nullable [] buf) {
        if (buf == null) {
            return;
        }
        if (buf.length == BUFFER_SIZE) {
            returnTo(GLOBAL_BUF_POOL, MASK, buf);
        } else if (buf.length == LARGE_BUFFER_SIZE) {
            returnTo(LARGE_BUF_POOL, LARGE_MASK, buf);
        }
    }

    private static byte[] borrowFrom(AtomicReferenceArray<byte[]> pool, int mask, int size) {
        int start = ThreadLocalRandom.current().nextInt();
        for (int i = 0; i < PROBE_LIMIT; i++) {
            int idx = (start + i) & mask;
            byte[] pooled = pool.getAndSet(idx, null);
            if (pooled != null) {
                return pooled;
            }
        }
        return new byte[size];
    }

    private static void returnTo(AtomicReferenceArray<byte[]> pool, int mask, byte[] buf) {
        int start = ThreadLocalRandom.current().nextInt();
        for (int i = 0; i < PROBE_LIMIT; i++) {
            int idx = (start + i) & mask;
            if (pool.compareAndSet(idx, null, buf)) {
                return;
            }
        }
    }

    public static <T> @Nullable T readFromStream(InputStream is, JsonReader<T> reader) throws IOException {
        byte[] buf = borrowBuffer();
        int offset = 0;
        try {
            int read;
            while ((read = is.read(buf, offset, buf.length - offset)) != -1) {
                offset += read;
                if (offset == buf.length) {
                    int newLen = buf.length * 2;
                    byte[] nextBuf = (newLen == LARGE_BUFFER_SIZE) ? borrowLargeBuffer() : new byte[newLen];
                    System.arraycopy(buf, 0, nextBuf, 0, offset);
                    returnBuffer(buf);
                    buf = nextBuf;
                }
            }
            if (offset == 0) {
                return null;
            }
            return reader.read(buf, 0, offset);
        } finally {
            returnBuffer(buf);
        }
    }
}
