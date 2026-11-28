package io.koraframework.http.common.body;

import org.jspecify.annotations.*;

import java.io.*;
import java.nio.*;

public interface HttpBodySink {

    byte[] EMPTY_BYTES = new byte[0];

    void acceptBytes(byte[] bytes, int offset, int length, @Nullable Runnable releaser);

    default void acceptBytes(byte[] bytes, int offset, int length) {
        acceptBytes(bytes, offset, length, null);
    }

    ByteBuffer allocateBuffer(int exactSize);

    void acceptAllocatedBuffer(ByteBuffer buffer);

    default @Nullable Object takeBuffer() {
        return null;
    }

    default void release() {
    }

    final class FixedByteArrayOutputStream extends OutputStream {
        private final byte[] buf;
        private int count;

        public FixedByteArrayOutputStream(byte[] buf) {
            this.buf = buf;
        }

        @Override
        public void write(int b) {
            buf[count++] = (byte) b;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            System.arraycopy(b, off, buf, count, len);
            count += len;
        }
    }

    final class ByteBufferOutputStream extends OutputStream {
        private final ByteBuffer buffer;

        public ByteBufferOutputStream(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public void write(int b) {
            buffer.put((byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            buffer.put(b, off, len);
        }
    }
}
