package io.koraframework.scheduling.jdk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(15)
class VirtualThreadSchedulingJdkExecutorTest {

    private final List<VirtualThreadSchedulingJdkExecutor> executors = new ArrayList<>();

    @AfterEach
    void cleanup() {
        this.executors.forEach(VirtualThreadSchedulingJdkExecutor::release);
    }

    @Test
    void usesOnePlatformTimerAndLimitsVirtualThreadExecution() throws Exception {
        var executor = executor(2);
        var started = new CountDownLatch(2);
        var unblock = new CountDownLatch(1);
        var running = new AtomicInteger();
        var maxRunning = new AtomicInteger();
        var threads = java.util.concurrent.ConcurrentHashMap.<Thread>newKeySet();
        var futures = new ArrayList<ScheduledFuture<?>>();
        try {
            for (var i = 0; i < 8; i++) {
                futures.add(executor.scheduleOnce(() -> {
                    threads.add(Thread.currentThread());
                    maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                    started.countDown();
                    try {
                        await(unblock);
                    } finally {
                        running.decrementAndGet();
                    }
                }, 0, TimeUnit.NANOSECONDS));
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(threads).hasSize(2);
            assertThat(Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().equals("kora-jdk-scheduler-timer")).toList()).hasSize(1);
        } finally {
            unblock.countDown();
        }
        for (var future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        assertThat(maxRunning.get()).isEqualTo(2);
        assertThat(threads).hasSize(8).allMatch(Thread::isVirtual);
    }

    @Test
    void worksWithoutRegisteredJobsAndFutureWaitsForJobCompletion() throws Exception {
        var executor = executor(0);
        var started = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        var thread = new AtomicReference<Thread>();
        try {
            var future = executor.scheduleOnce(() -> {
                thread.set(Thread.currentThread());
                started.countDown();
                await(unblock);
            }, -1, TimeUnit.SECONDS);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(future.isDone()).isFalse();
            unblock.countDown();
            assertThat(future.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(thread.get().isVirtual()).isTrue();
        } finally {
            unblock.countDown();
        }
    }

    @Test
    void fixedDelayStartsAfterPreviousExecutionCompletes() throws Exception {
        var executor = executor(2);
        var firstStarted = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        var runs = new AtomicInteger();
        var firstEnd = new AtomicLong();
        var secondStart = new AtomicLong();
        var future = executor.scheduleWithFixedDelay(() -> {
            if (runs.incrementAndGet() == 1) {
                firstStarted.countDown();
                await(unblock);
                firstEnd.set(System.nanoTime());
            } else {
                secondStart.compareAndSet(0, System.nanoTime());
                secondStarted.countDown();
            }
        }, 0, 150, TimeUnit.MILLISECONDS);
        try {
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondStarted.await(250, TimeUnit.MILLISECONDS)).isFalse();
            unblock.countDown();
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondStart.get() - firstEnd.get()).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(150));
        } finally {
            future.cancel(true);
            unblock.countDown();
        }
    }

    @Test
    void fixedRateCatchesUpWithoutOverlappingExecutions() throws Exception {
        var executor = executor(2);
        var firstStarted = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        var runs = new AtomicInteger();
        var firstEnd = new AtomicLong();
        var secondStart = new AtomicLong();
        var future = executor.scheduleAtFixedRate(() -> {
            if (runs.incrementAndGet() == 1) {
                firstStarted.countDown();
                await(unblock);
                firstEnd.set(System.nanoTime());
            } else {
                secondStart.compareAndSet(0, System.nanoTime());
                secondStarted.countDown();
            }
        }, 0, 1, TimeUnit.SECONDS);
        try {
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondStarted.await(1200, TimeUnit.MILLISECONDS)).isFalse();
            unblock.countDown();
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondStart.get() - firstEnd.get()).isBetween(0L, TimeUnit.MILLISECONDS.toNanos(900));
        } finally {
            future.cancel(true);
            unblock.countDown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void periodicFailureCompletesFutureAndSuppressesFurtherExecutions(boolean fixedRate) throws Exception {
        var executor = executor(1);
        var failure = new IllegalStateException("job failed");
        var calls = new AtomicInteger();
        Runnable job = () -> {
            calls.incrementAndGet();
            throw failure;
        };
        var future = fixedRate
            ? executor.scheduleAtFixedRate(job, 0, 1, TimeUnit.MILLISECONDS)
            : executor.scheduleWithFixedDelay(job, 0, 1, TimeUnit.MILLISECONDS);
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class).hasCause(failure);
        executor.scheduleOnce(() -> {}, 30, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void oneShotFailureIsReportedByFuture() {
        var executor = executor(1);
        var failure = new IllegalArgumentException("job failed");
        var future = executor.scheduleOnce(() -> { throw failure; }, 0, TimeUnit.NANOSECONDS);
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class).hasCause(failure);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancellationControlsInterruptionOfActualJob(boolean interrupt) throws Exception {
        var executor = executor(1);
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        var future = executor.scheduleOnce(() -> {
            started.countDown();
            try {
                unblock.await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        }, 0, TimeUnit.NANOSECONDS);
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(future.cancel(interrupt)).isTrue();
            assertThat(future.isCancelled()).isTrue();
            assertThat(interrupted.await(interrupt ? 5000 : 100, TimeUnit.MILLISECONDS)).isEqualTo(interrupt);
        } finally {
            unblock.countDown();
        }
    }

    @Test
    void cancelledDelayedAndQueuedJobsNeverRun() throws Exception {
        var executor = executor(1);
        var started = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try {
            executor.scheduleOnce(() -> {
                started.countDown();
                await(unblock);
            }, 0, TimeUnit.NANOSECONDS);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var queued = executor.scheduleOnce(calls::incrementAndGet, 0, TimeUnit.NANOSECONDS);
            var delayed = executor.scheduleOnce(calls::incrementAndGet, 1, TimeUnit.DAYS);
            assertThat(queued.cancel(true)).isTrue();
            assertThat(delayed.cancel(false)).isTrue();
            unblock.countDown();
            executor.scheduleOnce(() -> {}, 0, TimeUnit.NANOSECONDS).get(5, TimeUnit.SECONDS);
            assertThat(calls.get()).isZero();
        } finally {
            unblock.countDown();
        }
    }

    @Test
    void gracefulReleaseExecutesDelayedOneShotAndCancelsPeriodicJob() throws Exception {
        var executor = executor(1);
        var once = executor.scheduleOnce(() -> {}, 30, TimeUnit.MILLISECONDS);
        var periodic = executor.scheduleAtFixedRate(() -> {}, 1, 1, TimeUnit.DAYS);
        executor.release();
        assertThat(once.get(5, TimeUnit.SECONDS)).isNull();
        assertThat(periodic.isCancelled()).isTrue();
        assertThatThrownBy(() -> executor.scheduleOnce(() -> {}, 0, TimeUnit.SECONDS))
            .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
    }

    @Test
    void releaseTimeoutInterruptsRunningJobAndCancelsOutstandingFutures() throws Exception {
        var executor = executor(1, Duration.ofMillis(100));
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var running = executor.scheduleOnce(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        }, 0, TimeUnit.NANOSECONDS);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        var queued = executor.scheduleOnce(() -> {}, 0, TimeUnit.NANOSECONDS);
        var delayed = executor.scheduleOnce(() -> {}, 1, TimeUnit.DAYS);
        executor.release();
        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(running.isCancelled()).isTrue();
        assertThat(queued.isCancelled()).isTrue();
        assertThat(delayed.isCancelled()).isTrue();
    }

    @Test
    void interruptedReleasePreservesInterruptFlagAndCancelsJobs() {
        var executor = executor(1);
        var future = executor.scheduleOnce(() -> {}, 1, TimeUnit.DAYS);
        Thread.currentThread().interrupt();
        try {
            executor.release();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(future.isCancelled()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsNonPositivePeriods() {
        var executor = executor(1);
        assertThatThrownBy(() -> executor.scheduleAtFixedRate(() -> {}, 0, 0, TimeUnit.SECONDS))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> executor.scheduleWithFixedDelay(() -> {}, 0, -1, TimeUnit.SECONDS))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private VirtualThreadSchedulingJdkExecutor executor(int maxConcurrentExecutions) {
        return executor(maxConcurrentExecutions, Duration.ofSeconds(2));
    }

    private VirtualThreadSchedulingJdkExecutor executor(int maxConcurrentExecutions, Duration shutdownWait) {
        var executor = new VirtualThreadSchedulingJdkExecutor(new SchedulingJdkConfig() {
            @Override
            public Duration shutdownWait() {
                return shutdownWait;
            }

            @Override
            public int maxConcurrentExecutions() {
                return maxConcurrentExecutions;
            }
        });
        executor.init();
        this.executors.add(executor);
        return executor;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
