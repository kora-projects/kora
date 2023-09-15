package ru.tinkoff.kora.common.util;

import java.io.InputStream;
import java.nio.ByteBuffer;

public final class ByteBufferInputStream extends InputStream {
    private final ByteBuffer buffer;

    public ByteBufferInputStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public int available() {
        return this.buffer.remaining();
    }

    public int read() {
        if (!this.buffer.hasRemaining()) {
            return -1;
        }
        return this.buffer.get() & 0xFF;
    }

    public int read(byte[] bytes, int off, int len) {
        if (len == 0) {
            return 0;
        }
        if (!this.buffer.hasRemaining()) {
            return -1;
        }

        len = Math.min(len, buffer.remaining());
        this.buffer.get(bytes, off, len);
        return len;
    }
}
