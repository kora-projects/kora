package io.koraframework.common.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitedVirtualThreadPerTaskExecutorTest {

    private static List<Supplier<LimitedVirtualThreadPerTaskExecutor>> queuedExecutors() {
        return List.of(
            () -> new LimitedVirtualThreadPerTaskExecutor(1),
            () -> new LimitedVirtualThreadPerTaskExecutor(1, "queued-vt"),
            () -> new LimitedVirtualThreadPerTaskExecutor(1,
                Thread.ofVirtual().name("custom-vt-", 0).inheritInheritableThreadLocals(true))
        );
    }

    @ParameterizedTest
    @MethodSource("queuedExecutors")
    void tasksDoNotInheritSubmitterThreadLocals(Supplier<LimitedVirtualThreadPerTaskExecutor> factory) throws Exception {
        var context = new InheritableThreadLocal<String>();
        context.set("submitter");
        try (var executor = factory.get()) {
            assertThat(executor.submit(context::get).get(5, TimeUnit.SECONDS)).isNull();
            assertThat(context.get()).isEqualTo("submitter");
        } finally {
            context.remove();
        }
    }

    @ParameterizedTest
    @MethodSource("queuedExecutors")
    void queuedTasksDoNotInheritPreviousTaskThreadLocals(Supplier<LimitedVirtualThreadPerTaskExecutor> factory) throws Exception {
        var context = new InheritableThreadLocal<String>();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = factory.get();
        try {
            executor.execute(() -> {
                context.set("previous-task");
                started.countDown();
                await(release);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            context.set("submitter");
            var queued = executor.submit(context::get);
            release.countDown();

            assertThat(queued.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(context.get()).isEqualTo("submitter");
        } finally {
            context.remove();
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shutdownDrainsQueueAndRejectsNewTasks() throws Exception {
        var executor = new LimitedVirtualThreadPerTaskExecutor(1);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                await(release);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var queued = executor.submit(() -> 42);

            executor.shutdown();

            assertThat(executor.isShutdown()).isTrue();
            assertThat(executor.isTerminated()).isFalse();
            assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
            release.countDown();
            assertThat(queued.get(5, TimeUnit.SECONDS)).isEqualTo(42);
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shutdownNowReturnsQueuedTasksAndWaitsForRunningTaskToFinish() throws Exception {
        var executor = new LimitedVirtualThreadPerTaskExecutor(1);
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                while (release.getCount() != 0) {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                    }
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Runnable queued = () -> {};
            executor.execute(queued);
            var future = executor.submit(() -> 42);

            var notStarted = executor.shutdownNow();
            assertThat(notStarted).hasSize(2);
            assertThat(notStarted.get(0)).isSameAs(queued);
            assertThat(notStarted.get(1)).isSameAs(future);
            assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.isShutdown()).isTrue();
            assertThat(executor.awaitTermination(0, TimeUnit.SECONDS)).isFalse();
            assertThat(future.isDone()).isFalse();
            future.cancel(false);

            release.countDown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.shutdownNow()).isEmpty();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void closeFromOwnTaskFailsWithoutShuttingDownExecutor() throws Exception {
        try (var executor = new LimitedVirtualThreadPerTaskExecutor(1)) {
            executor.submit(() -> assertThatThrownBy(executor::close)
                .isInstanceOf(IllegalStateException.class)).get(5, TimeUnit.SECONDS);
            assertThat(executor.isShutdown()).isFalse();
            assertThat(executor.submit(() -> 42).get(5, TimeUnit.SECONDS)).isEqualTo(42);
        }
    }

    @Test
    void executorsUseConfiguredThreadPoolName() throws Exception {
        assertThreadName(() -> new LimitedVirtualThreadPerTaskExecutor(1, "queued-vt"), "queued-vt-");
    }

    @Test
    void nonBlockingExecutorDoesNotBlockSubmitterWhenLimitIsReached() throws Exception {
        var executor = new LimitedVirtualThreadPerTaskExecutor(1);
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
        var executor = new LimitedVirtualThreadPerTaskExecutor(2);
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
