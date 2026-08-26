package io.koraframework.http.server.undertow;

import io.undertow.UndertowMessages;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.jspecify.annotations.Nullable;
import org.xnio.XnioIoThread;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Lock-free replacement for {@link io.undertow.server.DefaultByteBufferPool}.
 *
 * <p>{@code DefaultByteBufferPool} keeps its per-thread caches in a single
 * {@code Collections.synchronizedMap(new WeakHashMap<Thread, ...>())}, so every allocate and every free on the whole
 * server serializes on one monitor. Under a virtual-thread-per-connection dispatch that monitor is the dominant
 * scalability limit: buffers are allocated on the XNIO thread and released on the request virtual thread, so the
 * per-thread cache never hits, while the map itself grows a weak entry per virtual thread and expunges stale keys
 * inside the critical section.
 *
 * <p>XNIO I/O threads have a small cache addressed directly by their stable worker-thread number. This fast path uses
 * only plain array access because each cache has one owner. All other buffers live in a fixed, globally shared slot
 * array striped to spread CAS traffic; a thread starts probing at its home stripe and falls through to the rest, so a
 * buffer released by a virtual thread is reusable by any I/O thread. No cache is ever created for a virtual thread.
 * Retained memory per direct/heap tier is bounded by {@code stripes * (16 + 4) * bufferSize}, regardless of request
 * or virtual-thread count. A direct pool also owns the separate heap tier required by Undertow's array-backed API.
 *
 * <p>The common XNIO acquire/release path has no atomic operation. Cross-thread acquire and release use a bounded scan
 * of volatile reads plus a single CAS on success. There is no lock, thread map, per-VT state, {@code WeakReference}
 * bookkeeping or {@code finalize()}.
 */
public final class KoraByteBufferPool implements ByteBufferPool {

    private static final int SLOTS_PER_STRIPE = 16;
    private static final int IO_THREAD_CACHE_SIZE = 4;
    /** Fibonacci hashing constant, used to spread thread ids over stripes. */
    private static final long STRIPE_HASH = 0x9E3779B97F4A7C15L;

    private final boolean direct;
    private final int bufferSize;
    private final int stripeMask;
    private final AtomicReferenceArray<ByteBuffer> slots;
    private final ByteBuffer[][] ioThreadCaches;
    private final int[] ioThreadCacheSizes;
    private final ByteBufferPool arrayBackedPool;

    private volatile boolean closed = false;

    /**
     * Creates a pool sized from the available processors, mirroring the buffer size {@code Undertow.Builder} picks for
     * the current heap.
     */
    public KoraByteBufferPool() {
        this(defaultDirect(), defaultBufferSize(), defaultStripes());
    }

    /**
     * @param direct     whether pooled buffers are allocated off-heap
     * @param bufferSize size of every pooled buffer
     * @param stripes    number of independent slot stripes and XNIO thread caches, rounded up to a power of two. The
     *                   global cross-thread tier retains at most {@code stripes * 16} buffers, plus four per I/O cache.
     */
    public KoraByteBufferPool(boolean direct, int bufferSize, int stripes) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be positive, but was " + bufferSize);
        }
        if (stripes <= 0) {
            throw new IllegalArgumentException("stripes must be positive, but was " + stripes);
        }
        var stripeCount = (stripes == 1) ? 1 : Integer.highestOneBit(stripes - 1) << 1;
        this.direct = direct;
        this.bufferSize = bufferSize;
        this.stripeMask = stripeCount - 1;
        this.slots = new AtomicReferenceArray<>(stripeCount * SLOTS_PER_STRIPE);
        this.ioThreadCaches = new ByteBuffer[stripeCount][IO_THREAD_CACHE_SIZE];
        this.ioThreadCacheSizes = new int[stripeCount];
        // Undertow asks for an array backed pool when it has to copy a direct buffer through a heap array
        this.arrayBackedPool = direct
            ? new KoraByteBufferPool(false, bufferSize, stripeCount)
            : this;
    }

    @Override
    public PooledByteBuffer allocate() {
        if (this.closed) {
            throw UndertowMessages.MESSAGES.poolIsClosed();
        }
        var buffer = this.pollIoThread();
        if (buffer == null) {
            buffer = this.poll();
        }
        if (buffer == null) {
            buffer = this.direct
                ? ByteBuffer.allocateDirect(this.bufferSize)
                : ByteBuffer.allocate(this.bufferSize);
        } else {
            buffer.clear();
        }
        return new KoraPooledByteBuffer(this, buffer);
    }

    @Override
    public ByteBufferPool getArrayBackedPool() {
        return this.arrayBackedPool;
    }

    @Override
    public int getBufferSize() {
        return this.bufferSize;
    }

    @Override
    public boolean isDirect() {
        return this.direct;
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        for (int i = 0; i < this.slots.length(); i++) {
            this.slots.set(i, null);
        }
        for (int i = 0; i < this.ioThreadCaches.length; i++) {
            var cache = this.ioThreadCaches[i];
            for (int j = 0; j < cache.length; j++) {
                cache[j] = null;
            }
            this.ioThreadCacheSizes[i] = 0;
        }
        if (this.arrayBackedPool != this) {
            this.arrayBackedPool.close();
        }
    }

    /**
     * Takes a buffer out of the pool, or returns {@code null} if every slot is empty. Probing starts at the calling
     * thread's home stripe and wraps around the whole array, so a buffer released on any thread is visible here.
     */
    @Nullable
    private ByteBuffer poll() {
        var length = this.slots.length();
        var start = this.homeSlot();
        for (int i = 0; i < length; i++) {
            var index = start + i;
            if (index >= length) {
                index -= length;
            }
            var buffer = this.slots.get(index);
            if (buffer != null && this.slots.compareAndSet(index, buffer, null)) {
                return buffer;
            }
        }
        return null;
    }

    /**
     * Returns a buffer to the pool. When every slot is occupied the buffer is dropped: the pool never grows past its
     * configured capacity, and a dropped direct buffer is reclaimed by its own JDK cleaner on the next collection.
     * Explicitly deallocating it here would risk freeing memory another thread still holds.
     */
    private void free(ByteBuffer buffer) {
        if (this.closed) {
            return;
        }
        if (this.offerIoThread(buffer)) {
            return;
        }
        var length = this.slots.length();
        var start = this.homeSlot();
        for (int i = 0; i < length; i++) {
            var index = start + i;
            if (index >= length) {
                index -= length;
            }
            if (this.slots.get(index) == null && this.slots.compareAndSet(index, null, buffer)) {
                return;
            }
        }
    }

    /** Number of buffers currently held by the global cross-thread tier, visible for tests. */
    int retained() {
        var retained = 0;
        for (int i = 0; i < this.slots.length(); i++) {
            if (this.slots.get(i) != null) {
                retained++;
            }
        }
        return retained;
    }

    /** Total number of buffers the global cross-thread tier can hold. */
    int capacity() {
        return this.slots.length();
    }

    /**
     * First slot of the calling thread's home stripe. This is a plain field read and some arithmetic, not a thread
     * local lookup, and it stores nothing against the thread, so a virtual thread that runs once leaves no trace.
     */
    private int homeSlot() {
        var thread = Thread.currentThread();
        if (thread instanceof XnioIoThread ioThread) {
            var number = ioThread.getNumber();
            if (number >= 0 && number < this.ioThreadCaches.length) {
                return number * SLOTS_PER_STRIPE;
            }
        }
        var id = thread.threadId();
        var hash = (int) ((id * STRIPE_HASH) >>> 32);
        return (hash & this.stripeMask) * SLOTS_PER_STRIPE;
    }

    /**
     * XNIO assigns a stable number to each I/O thread. Each index therefore has one owner and needs no volatile access
     * or CAS. Virtual threads never enter this cache and leave no thread-local state behind.
     */
    @Nullable
    private ByteBuffer pollIoThread() {
        var thread = Thread.currentThread();
        if (!(thread instanceof XnioIoThread ioThread)) {
            return null;
        }
        var number = ioThread.getNumber();
        if (number < 0 || number >= this.ioThreadCaches.length) {
            return null;
        }
        var size = this.ioThreadCacheSizes[number];
        if (size == 0) {
            return null;
        }
        var index = size - 1;
        var buffer = this.ioThreadCaches[number][index];
        this.ioThreadCaches[number][index] = null;
        this.ioThreadCacheSizes[number] = index;
        return buffer;
    }

    private boolean offerIoThread(ByteBuffer buffer) {
        var thread = Thread.currentThread();
        if (!(thread instanceof XnioIoThread ioThread)) {
            return false;
        }
        var number = ioThread.getNumber();
        if (number < 0 || number >= this.ioThreadCaches.length) {
            return false;
        }
        var size = this.ioThreadCacheSizes[number];
        if (size == IO_THREAD_CACHE_SIZE) {
            return false;
        }
        this.ioThreadCaches[number][size] = buffer;
        this.ioThreadCacheSizes[number] = size + 1;
        return true;
    }

    private static boolean defaultDirect() {
        return Runtime.getRuntime().maxMemory() >= 64 * 1024 * 1024;
    }

    private static int defaultBufferSize() {
        var maxMemory = Runtime.getRuntime().maxMemory();
        if (maxMemory < 64 * 1024 * 1024) {
            return 512;
        } else if (maxMemory < 128 * 1024 * 1024) {
            return 1024;
        } else {
            return 1024 * 16 - 20; //the 20 is to allow some space for protocol headers, see UNDERTOW-1209
        }
    }

    private static int defaultStripes() {
        return Math.max(Runtime.getRuntime().availableProcessors(), 4);
    }

    private static final class KoraPooledByteBuffer implements PooledByteBuffer {

        private static final AtomicIntegerFieldUpdater<KoraPooledByteBuffer> openUpdater
            = AtomicIntegerFieldUpdater.newUpdater(KoraPooledByteBuffer.class, "open");

        private final KoraByteBufferPool pool;
        private @Nullable ByteBuffer buffer;
        private volatile int open = 1;

        private KoraPooledByteBuffer(KoraByteBufferPool pool, ByteBuffer buffer) {
            this.pool = pool;
            this.buffer = buffer;
        }

        @Override
        public ByteBuffer getBuffer() {
            var current = this.buffer;
            if (this.open == 0 || current == null) {
                throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
            }
            return current;
        }

        @Override
        public void close() {
            var current = this.buffer;
            // only the caller that wins the flip returns the buffer, so a double close cannot pool it twice
            if (current != null && openUpdater.compareAndSet(this, 1, 0)) {
                this.buffer = null;
                this.pool.free(current);
            }
        }

        @Override
        public boolean isOpen() {
            return this.open != 0;
        }

        @Override
        public String toString() {
            return "KoraPooledByteBuffer{buffer=" + this.buffer + ", open=" + this.open + '}';
        }
    }
}
