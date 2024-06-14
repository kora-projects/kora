package ru.tinkoff.kora.http.server.undertow.pool;

import io.undertow.UndertowMessages;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.DirectByteBufferDeallocator;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * DefaultByteBufferPool with fixed thread leak
 *
 * @see DefaultByteBufferPool
 */
public class KoraByteBufferPool implements ByteBufferPool {
    private static final Cleaner cleaner = Cleaner.create();

    private final ThreadLocal<ThreadLocalData> threadLocalCache = new ThreadLocal<>();
    private final ConcurrentLinkedQueue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();

    private final boolean direct;
    private final int bufferSize;
    private final int maximumPoolSize;
    private final int threadLocalCacheSize;
    private final int leakDectionPercent;
    private int count; //racily updated count used in leak detection

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile int currentQueueLength = 0;
    private static final AtomicIntegerFieldUpdater<KoraByteBufferPool> currentQueueLengthUpdater = AtomicIntegerFieldUpdater.newUpdater(KoraByteBufferPool.class, "currentQueueLength");

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile int reclaimedThreadLocals = 0;
    private static final AtomicIntegerFieldUpdater<KoraByteBufferPool> reclaimedThreadLocalsUpdater = AtomicIntegerFieldUpdater.newUpdater(KoraByteBufferPool.class, "reclaimedThreadLocals");

    private volatile boolean closed;

    private final KoraByteBufferPool arrayBackedPool;


    /**
     * @param direct     If this implementation should use direct buffers
     * @param bufferSize The buffer size to use
     */
    public KoraByteBufferPool(boolean direct, int bufferSize) {
        this(direct, bufferSize, -1, 12, 0);
    }

    /**
     * @param direct               If this implementation should use direct buffers
     * @param bufferSize           The buffer size to use
     * @param maximumPoolSize      The maximum pool size, in number of buffers, it does not include buffers in thread local caches
     * @param threadLocalCacheSize The maximum number of buffers that can be stored in a thread local cache
     */
    public KoraByteBufferPool(boolean direct, int bufferSize, int maximumPoolSize, int threadLocalCacheSize, int leakDecetionPercent) {
        this.direct = direct;
        this.bufferSize = bufferSize;
        this.maximumPoolSize = maximumPoolSize;
        this.threadLocalCacheSize = threadLocalCacheSize;
        this.leakDectionPercent = leakDecetionPercent;
        if (direct) {
            arrayBackedPool = new KoraByteBufferPool(false, bufferSize, maximumPoolSize, 0, leakDecetionPercent);
        } else {
            arrayBackedPool = this;
        }
    }


    /**
     * @param direct               If this implementation should use direct buffers
     * @param bufferSize           The buffer size to use
     * @param maximumPoolSize      The maximum pool size, in number of buffers, it does not include buffers in thread local caches
     * @param threadLocalCacheSize The maximum number of buffers that can be stored in a thread local cache
     */
    public KoraByteBufferPool(boolean direct, int bufferSize, int maximumPoolSize, int threadLocalCacheSize) {
        this(direct, bufferSize, maximumPoolSize, threadLocalCacheSize, 0);
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public boolean isDirect() {
        return direct;
    }

    @Override
    public PooledByteBuffer allocate() {
        if (closed) {
            throw UndertowMessages.MESSAGES.poolIsClosed();
        }
        var buffer = (ByteBuffer) null;
        var local = (ThreadLocalData) null;
        if (threadLocalCacheSize > 0) {
            local = threadLocalCache.get();
            if (local != null) {
                buffer = local.buffers.poll();
            } else {
                local = new ThreadLocalData();
                threadLocalCache.set(local);
            }
        }
        if (buffer == null) {
            buffer = queue.poll();
            if (buffer != null) {
                currentQueueLengthUpdater.decrementAndGet(this);
                //buffer.clear();
            }
        }
        if (buffer == null) {
            if (direct) {
                buffer = ByteBuffer.allocateDirect(bufferSize);
            } else {
                buffer = ByteBuffer.allocate(bufferSize);
            }
        }
        if (local != null) {
            if (local.allocationDepth < threadLocalCacheSize) { //prevent overflow if the thread only allocates and never frees
                local.allocationDepth++;
            }
        }
        buffer.clear();
        return new DefaultPooledBuffer(this, buffer, leakDectionPercent == 0 ? false : (++count % 100 < leakDectionPercent));
    }

    @Override
    public ByteBufferPool getArrayBackedPool() {
        return arrayBackedPool;
    }

    private void freeInternal(ByteBuffer buffer) {
        if (closed) {
            DirectByteBufferDeallocator.free(buffer);
            return; //GC will take care of it
        }
        var local = threadLocalCache.get();
        if (local != null) {
            if (local.allocationDepth > 0) {
                local.allocationDepth--;
                if (local.buffers.size() < threadLocalCacheSize) {
                    local.buffers.add(buffer);
                    return;
                }
            }
        }
        queueIfUnderMax(buffer);
    }

    private void queueIfUnderMax(ByteBuffer buffer) {
        int size;
        do {
            size = currentQueueLength;
            if (size > maximumPoolSize) {
                DirectByteBufferDeallocator.free(buffer);
                return;
            }
        } while (!currentQueueLengthUpdater.compareAndSet(this, size, size + 1));
        queue.add(buffer);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        queue.clear();
    }

    private static class DefaultPooledBuffer implements PooledByteBuffer {

        private final KoraByteBufferPool pool;
        private final LeakDetector leakDetector;
        private ByteBuffer buffer;

        private volatile int referenceCount = 1;
        private static final AtomicIntegerFieldUpdater<DefaultPooledBuffer> referenceCountUpdater = AtomicIntegerFieldUpdater.newUpdater(DefaultPooledBuffer.class, "referenceCount");

        DefaultPooledBuffer(KoraByteBufferPool pool, ByteBuffer buffer, boolean detectLeaks) {
            this.pool = pool;
            this.buffer = buffer;

            if (detectLeaks) {
                var leakDetector = new LeakDetector();
                cleaner.register(this, leakDetector::onGarbageCollected);
                this.leakDetector = leakDetector;
            } else {
                this.leakDetector = null;
            }
        }

        @Override
        public ByteBuffer getBuffer() {
            final ByteBuffer tmp = this.buffer;
            //UNDERTOW-2072
            if (referenceCount == 0 || tmp == null) {
                throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
            }
            return tmp;
        }

        @Override
        public void close() {
            final ByteBuffer tmp = this.buffer;
            if (referenceCountUpdater.compareAndSet(this, 1, 0)) {
                this.buffer = null;
                if (leakDetector != null) {
                    leakDetector.closed = true;
                }
                pool.freeInternal(tmp);
            }
        }

        @Override
        public boolean isOpen() {
            return referenceCount > 0;
        }

        @Override
        public String toString() {
            return "DefaultPooledBuffer{" +
                "buffer=" + buffer +
                ", referenceCount=" + referenceCount +
                '}';
        }
    }

    private class ThreadLocalData {
        ArrayDeque<ByteBuffer> buffers = new ArrayDeque<>(threadLocalCacheSize);
        int allocationDepth = 0;

        public ThreadLocalData() {
            cleaner.register(this, () -> {
                reclaimedThreadLocalsUpdater.incrementAndGet(KoraByteBufferPool.this);
            });
        }
    }

    private static class LeakDetector {

        volatile boolean closed = false;
        private final Throwable allocationPoint;

        private LeakDetector() {
            this.allocationPoint = new Throwable("Buffer leak detected");
        }

        public void onGarbageCollected() {
            if (!closed) {
                allocationPoint.printStackTrace();
            }
        }
    }
}
