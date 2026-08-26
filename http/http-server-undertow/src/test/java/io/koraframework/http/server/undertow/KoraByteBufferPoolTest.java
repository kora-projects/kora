package io.koraframework.http.server.undertow;

import io.undertow.connector.PooledByteBuffer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KoraByteBufferPoolTest {

    @Test
    void allocatesRequestedBufferSize() {
        try (var pool = new KoraByteBufferPool(false, 1024, 4)) {
            try (var pooled = pool.allocate()) {
                assertThat(pooled.isOpen()).isTrue();
                assertThat(pooled.getBuffer().capacity()).isEqualTo(1024);
                assertThat(pooled.getBuffer().remaining()).isEqualTo(1024);
            }
        }
    }

    @Test
    void reusesReleasedBuffer() {
        try (var pool = new KoraByteBufferPool(false, 1024, 4)) {
            ByteBuffer first;
            try (var pooled = pool.allocate()) {
                first = pooled.getBuffer();
                first.put((byte) 42);
            }
            try (var pooled = pool.allocate()) {
                assertThat(pooled.getBuffer()).isSameAs(first);
                //a recycled buffer must come back cleared, not at the previous position
                assertThat(pooled.getBuffer().position()).isZero();
                assertThat(pooled.getBuffer().remaining()).isEqualTo(1024);
            }
        }
    }

    @Test
    void bufferReleasedOnAnotherThreadIsReusedByTheFirst() throws Exception {
        try (var pool = new KoraByteBufferPool(false, 1024, 8)) {
            var pooled = pool.allocate();
            var buffer = pooled.getBuffer();
            //undertow allocates on the XNIO thread and releases on the request thread
            var releaser = Thread.ofVirtual().start(pooled::close);
            releaser.join();

            try (var reacquired = pool.allocate()) {
                assertThat(reacquired.getBuffer()).isSameAs(buffer);
            }
        }
    }

    @Test
    void doubleCloseReleasesBufferOnlyOnce() {
        try (var pool = new KoraByteBufferPool(false, 1024, 4)) {
            var pooled = pool.allocate();
            var buffer = pooled.getBuffer();
            pooled.close();
            pooled.close();
            assertThat(pooled.isOpen()).isFalse();
            assertThatThrownBy(pooled::getBuffer).isInstanceOf(IllegalStateException.class);

            //a buffer pooled twice would be handed to two owners at once
            var first = pool.allocate();
            var second = pool.allocate();
            assertThat(first.getBuffer()).isSameAs(buffer);
            assertThat(second.getBuffer()).isNotSameAs(buffer);
            first.close();
            second.close();
        }
    }

    @Test
    void allocateOnClosedPoolFails() {
        var pool = new KoraByteBufferPool(false, 1024, 4);
        pool.close();
        assertThatThrownBy(pool::allocate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void directPoolExposesSeparateArrayBackedPool() {
        try (var pool = new KoraByteBufferPool(true, 1024, 4)) {
            var arrayBacked = pool.getArrayBackedPool();
            assertThat(pool.isDirect()).isTrue();
            assertThat(arrayBacked.isDirect()).isFalse();
            assertThat(arrayBacked.getBufferSize()).isEqualTo(1024);
            assertThat(arrayBacked.getArrayBackedPool()).isSameAs(arrayBacked);
            try (var pooled = arrayBacked.allocate()) {
                assertThat(pooled.getBuffer().hasArray()).isTrue();
            }
        }
    }

    @Test
    void heapPoolIsItsOwnArrayBackedPool() {
        try (var pool = new KoraByteBufferPool(false, 1024, 4)) {
            assertThat(pool.getArrayBackedPool()).isSameAs(pool);
        }
    }

    @Test
    void neverHandsTheSameBufferToTwoOwners() throws Exception {
        var threads = 64;
        var iterations = 2_000;
        var inUse = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<ByteBuffer, Boolean>()));
        var failure = new AtomicReference<Throwable>();
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);

        try (var pool = new KoraByteBufferPool(false, 256, 8); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < iterations; i++) {
                            try (var pooled = pool.allocate()) {
                                var buffer = pooled.getBuffer();
                                if (!inUse.add(buffer)) {
                                    throw new AssertionError("buffer handed out while already in use: " + buffer);
                                }
                                //yield while holding it, so a carrier shared cache would be caught here
                                Thread.yield();
                                inUse.remove(buffer);
                            }
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await();
        }

        assertThat(failure.get()).isNull();
    }

    @Test
    void crossThreadHandoffKeepsEveryBufferAccountedFor() throws Exception {
        var producers = 16;
        var iterations = 1_000;
        var handoff = new LinkedTransferQueue<PooledByteBuffer>();
        var released = new AtomicInteger();
        var failure = new AtomicReference<Throwable>();

        try (var pool = new KoraByteBufferPool(false, 256, 8)) {
            //one consumer releases everything the producers allocate, the pattern that defeats a thread affine cache
            var consumer = Thread.ofVirtual().start(() -> {
                try {
                    for (int i = 0; i < producers * iterations; i++) {
                        var pooled = handoff.take();
                        pooled.getBuffer().put(0, (byte) 1);
                        pooled.close();
                        released.incrementAndGet();
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });

            var threads = new ArrayList<Thread>();
            for (int p = 0; p < producers; p++) {
                threads.add(Thread.ofVirtual().start(() -> {
                    for (int i = 0; i < iterations; i++) {
                        handoff.put(pool.allocate());
                    }
                }));
            }
            for (var thread : threads) {
                thread.join();
            }
            consumer.join();
        }

        assertThat(failure.get()).isNull();
        assertThat(released.get()).isEqualTo(producers * iterations);
    }

    @Test
    void retainedBufferCountDoesNotGrowWithThreadCount() throws Exception {
        var stripes = 4;

        try (var pool = new KoraByteBufferPool(false, 256, stripes); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var capacity = pool.capacity();
            assertThat(capacity).isEqualTo(stripes * 16);

            //10k virtual threads, each touching the pool once and dying. A thread affine cache would end up holding a
            //buffer per thread here, which is exactly what DefaultByteBufferPool does.
            var done = new CountDownLatch(10_000);
            for (int i = 0; i < 10_000; i++) {
                executor.submit(() -> {
                    try (var pooled = pool.allocate()) {
                        pooled.getBuffer().put(0, (byte) 7);
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await();

            assertThat(pool.retained()).isLessThanOrEqualTo(capacity);

            //draining hands every retained buffer out exactly once
            Set<ByteBuffer> drained = Collections.newSetFromMap(new IdentityHashMap<>());
            var held = new ArrayList<PooledByteBuffer>();
            for (int i = 0; i < capacity; i++) {
                var pooled = pool.allocate();
                held.add(pooled);
                assertThat(drained.add(pooled.getBuffer())).isTrue();
            }
            assertThat(pool.retained()).isZero();
            held.forEach(PooledByteBuffer::close);
            assertThat(pool.retained()).isEqualTo(capacity);
        }
    }
}
