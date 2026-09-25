package io.koraframework.http.server.undertow;

import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

final class UnboundedThreadLocalByteBufferPool implements ByteBufferPool {

    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int MAX_BUFFERS_PER_THREAD = 128;

    private final ThreadLocal<ArrayDeque<ByteBuffer>> buffers = ThreadLocal.withInitial(ArrayDeque::new);
    private final boolean direct;

    private volatile ByteBufferPool arrayBackedPool;

    UnboundedThreadLocalByteBufferPool(boolean direct) {
        this.direct = direct;
    }

    @Override
    public PooledByteBuffer allocate() {
        var queue = this.buffers.get();
        var buffer = queue.pollFirst();
        if (buffer == null) {
            buffer = this.direct
                ? ByteBuffer.allocateDirect(BUFFER_SIZE)
                : ByteBuffer.allocate(BUFFER_SIZE);
        }
        buffer.clear();
        return new ThreadLocalPooledByteBuffer(this, buffer);
    }

    @Override
    public ByteBufferPool getArrayBackedPool() {
        if (!this.direct) {
            return this;
        }

        var pool = this.arrayBackedPool;
        if (pool == null) {
            synchronized (this) {
                pool = this.arrayBackedPool;
                if (pool == null) {
                    pool = new UnboundedThreadLocalByteBufferPool(false);
                    this.arrayBackedPool = pool;
                }
            }
        }
        return pool;
    }

    @Override
    public void close() {
    }

    @Override
    public int getBufferSize() {
        return BUFFER_SIZE;
    }

    @Override
    public boolean isDirect() {
        return this.direct;
    }

    private void free(ByteBuffer buffer) {
        var queue = this.buffers.get();
        if (queue.size() < MAX_BUFFERS_PER_THREAD) {
            buffer.clear();
            queue.addFirst(buffer);
        }
    }

    private static final class ThreadLocalPooledByteBuffer implements PooledByteBuffer {

        private final UnboundedThreadLocalByteBufferPool pool;
        private ByteBuffer buffer;

        private ThreadLocalPooledByteBuffer(UnboundedThreadLocalByteBufferPool pool, ByteBuffer buffer) {
            this.pool = pool;
            this.buffer = buffer;
        }

        @Override
        public ByteBuffer getBuffer() {
            var buffer = this.buffer;
            if (buffer == null) {
                throw new IllegalStateException("Buffer has already been freed");
            }
            return buffer;
        }

        @Override
        public void close() {
            var buffer = this.buffer;
            if (buffer != null) {
                this.buffer = null;
                this.pool.free(buffer);
            }
        }

        @Override
        public boolean isOpen() {
            return this.buffer != null;
        }

        @Override
        public String toString() {
            return "ThreadLocalPooledByteBuffer{buffer=" + this.buffer + '}';
        }
    }
}
