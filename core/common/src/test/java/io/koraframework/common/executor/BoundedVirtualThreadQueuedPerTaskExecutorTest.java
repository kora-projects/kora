package io.koraframework.common.executor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedVirtualThreadQueuedPerTaskExecutorTest {

    @Test
    void executorsUseConfiguredThreadPoolName() throws Exception {
        assertThreadName(() -> new BoundedVirtualThreadQueuedPerTaskExecutor(1, "queued-vt"), "queued-vt-");
        assertThreadName(() -> new BoundedVirtualThreadRunningPerTaskExecutor(1, "running-vt"), "running-vt-");
        assertThreadName(() -> new BoundedVirtualThreadBlockingPerTaskExecutor(1, "blocking-vt"), "blocking-vt-");
    }

    @Test
    void nonBlockingExecutorDoesNotBlockSubmitterWhenLimitIsReached() throws Exception {
        var executor = new BoundedVirtualThreadQueuedPerTaskExecutor(1);
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);

        executor.execute(() -> {
            firstStarted.countDown();
            await(releaseFirst);
        });
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

        executor.execute(secondStarted::countDown);

        assertThat(secondStarted.await(100, TimeUnit.MILLISECONDS)).isFalse();

        releaseFirst.countDown();
        assertThat(secondStarted.await(1, TimeUnit.SECONDS)).isTrue();

        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void nonBlockingExecutorLimitsConcurrentTaskBodies() throws Exception {
        var executor = new BoundedVirtualThreadQueuedPerTaskExecutor(2);
        var running = new AtomicInteger();
        var maxRunning = new AtomicInteger();
        var release = new CountDownLatch(1);
        var completed = new CountDownLatch(4);

        for (int i = 0; i < 4; i++) {
            executor.execute(() -> {
                var current = running.incrementAndGet();
                maxRunning.updateAndGet(previous -> Math.max(previous, current));
                try {
                    await(release);
                } finally {
                    running.decrementAndGet();
                    completed.countDown();
                }
            });
        }

        waitUntil(() -> maxRunning.get() == 2);
        release.countDown();
        assertThat(completed.await(1, TimeUnit.SECONDS)).isTrue();

        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void blockingExecutorBlocksSubmitterWhenLimitIsReached() throws Exception {
        var executor = new BoundedVirtualThreadBlockingPerTaskExecutor(1);
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondSubmitted = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);

        executor.execute(() -> {
            firstStarted.countDown();
            await(releaseFirst);
        });
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

        var submitter = Thread.ofVirtual().start(() -> {
            executor.execute(secondStarted::countDown);
            secondSubmitted.countDown();
        });

        assertThat(secondSubmitted.await(100, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(secondStarted.await(100, TimeUnit.MILLISECONDS)).isFalse();

        releaseFirst.countDown();
        submitter.join(1000);

        assertThat(secondSubmitted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(secondStarted.await(1, TimeUnit.SECONDS)).isTrue();

        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void blockingExecutorRejectsWaitingSubmitterAfterShutdownNow() throws Exception {
        var executor = new BoundedVirtualThreadBlockingPerTaskExecutor(1);
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var rejected = new CountDownLatch(1);

        executor.execute(() -> {
            firstStarted.countDown();
            await(releaseFirst);
        });
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

        var submitter = Thread.ofVirtual().start(() -> {
            try {
                executor.execute(() -> {});
            } catch (RejectedExecutionException e) {
                rejected.countDown();
            }
        });

        assertThat(rejected.await(100, TimeUnit.MILLISECONDS)).isFalse();

        executor.shutdownNow();
        releaseFirst.countDown();
        submitter.join(1000);

        assertThat(rejected.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }

    private static void assertThreadName(Supplier<ExecutorService> executorSupplier, String expectedPrefix) throws Exception {
        try (var executor = executorSupplier.get()) {
            var threadName = executor.submit(() -> Thread.currentThread().getName()).get(1, TimeUnit.SECONDS);

            assertThat(threadName).startsWith(expectedPrefix);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
