package io.koraframework.resilient.bulkhead;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import io.koraframework.common.Principal;
import io.koraframework.logging.common.MDC;
import io.koraframework.resilient.bulkhead.exception.BulkheadFullException;
import io.koraframework.resilient.bulkhead.exception.BulkheadFullException.Reason;
import io.koraframework.resilient.bulkhead.telemetry.impl.NoopBulkheadTelemetry;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class BulkheadModesTests {

    record Config(
        int maxConcurrentCalls,
        int maxQueuedCalls,
        Duration maxWaitDuration,
        BulkheadConfig.Type type,
        BulkheadConfig.AdaptiveConfig adaptive
    ) implements BulkheadConfig {

        @Override
        public TelemetryConfig telemetry() {
            return null;
        }
    }

    static Config config(int cap, int queue) {
        return new Config(cap, queue, queue == 0 ? Duration.ZERO : Duration.ofSeconds(10), BulkheadConfig.Type.FIXED, null);
    }

    static KoraBulkhead create(BulkheadConfig config) {
        return new KoraBulkhead("orders", config, NoopBulkheadTelemetry.INSTANCE);
    }

    static BulkheadConfig.AdaptiveConfig adaptive(int initial) {
        return new BulkheadConfig.AdaptiveConfig() {

            @Override
            public int initialLimit() {
                return initial;
            }

            @Override
            public double decreaseRatio() {
                return 0.5;
            }

            @Override
            public Duration targetDuration() {
                return Duration.ofMillis(50);
            }

            @Override
            public Duration samplingInterval() {
                return Duration.ofMillis(10);
            }

            @Override
            public int minimumSamples() {
                return 1;
            }
        };
    }

    @Test
    void boundedQueueIsFifoAndCannotBeBypassedByTryAcquire() {
        var bulkhead = create(config(1, 2));
        try {
            var running = bulkhead.acquire();
            var first = bulkhead.acquireAsync().toCompletableFuture();
            var second = bulkhead.acquireAsync().toCompletableFuture();
            assertEquals(2, bulkhead.queueLength());
            assertFalse(first.isDone());
            assertEquals(Reason.QUEUE_FULL, assertThrows(BulkheadFullException.class, bulkhead::acquireAsync).reason());
            assertNull(bulkhead.tryAcquire());
            running.close();
            assertTrue(first.isDone());
            assertFalse(second.isDone());
            assertEquals(1, bulkhead.inFlight());
            first.join().close();
            assertTrue(second.isDone());
            second.join().close();
            assertEquals(0, bulkhead.queueLength());
            assertEquals(0, bulkhead.inFlight());
        } finally {
            bulkhead.release();
        }
    }

    @Test
    void queueTimeoutRejectsWithoutStartingOperation() throws Exception {
        var bulkhead = create(new Config(1, 1, Duration.ofMillis(40), BulkheadConfig.Type.FIXED, null));
        try (var running = bulkhead.acquire()) {
            var result = bulkhead.executeAsync(() -> {
                fail("Timed out call started");
                return CompletableFuture.completedFuture("no");
            }).toCompletableFuture();
            var error = assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
            assertEquals(Reason.QUEUE_TIMEOUT, assertInstanceOf(BulkheadFullException.class, error.getCause()).reason());
            assertEquals(0, bulkhead.queueLength());
            assertEquals(1, bulkhead.inFlight());
            assertEquals(1, bulkhead.rejectedCalls());
        } finally {
            bulkhead.release();
        }
    }

    @Test
    void cancellingQueuedStageRemovesItAndDoesNotStartSupplier() {
        var bulkhead = create(config(1, 1));
        try (var running = bulkhead.acquire()) {
            var result = bulkhead.executeAsync(() -> {
                fail("Cancelled call started");
                return CompletableFuture.completedFuture("no");
            }).toCompletableFuture();
            assertEquals(1, bulkhead.queueLength());
            assertTrue(result.cancel(false));
            assertEquals(0, bulkhead.queueLength());
            assertEquals(1, bulkhead.inFlight());
            assertEquals(0, bulkhead.rejectedCalls());
        } finally {
            bulkhead.release();
        }
        assertEquals(0, bulkhead.inFlight());
    }

    @Test
    void syncWaitingIsInterruptibleAndRestoresInterruptFlag() throws Exception {
        var bulkhead = create(config(1, 1));
        var interrupted = new AtomicBoolean();
        var error = new CompletableFuture<Throwable>();
        try (var running = bulkhead.acquire()) {
            var thread = Thread.ofVirtual().start(() -> {
                try {
                    bulkhead.acquire().close();
                    error.complete(new AssertionError("Must wait"));
                } catch (Throwable failure) {
                    interrupted.set(Thread.currentThread().isInterrupted());
                    error.complete(failure);
                }
            });
            await().atMost(Duration.ofSeconds(5)).until(() -> bulkhead.queueLength() == 1);
            thread.interrupt();
            assertEquals(Reason.INTERRUPTED, assertInstanceOf(BulkheadFullException.class, error.get(5, TimeUnit.SECONDS)).reason());
            assertTrue(interrupted.get());
            assertEquals(0, bulkhead.queueLength());
        } finally {
            bulkhead.release();
        }
    }

    @Test
    void cancellationAndGrantRaceCannotLeakCapacity() throws Exception {
        var bulkhead = create(config(1, 1));
        try (var racers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 200; i++) {
                var running = bulkhead.acquire();
                var waiting = bulkhead.acquireAsync().toCompletableFuture();
                var start = new CountDownLatch(1);
                var close = racers.submit(() -> {
                    start.await();
                    running.close();
                    return null;
                });
                var cancel = racers.submit(() -> {
                    start.await();
                    waiting.cancel(false);
                    return null;
                });
                start.countDown();
                close.get(5, TimeUnit.SECONDS);
                cancel.get(5, TimeUnit.SECONDS);
                if (!waiting.isCancelled()) {
                    waiting.join().close();
                }
                assertEquals(0, bulkhead.inFlight());
                assertEquals(0, bulkhead.queueLength());
            }
        } finally {
            bulkhead.release();
        }
    }

    @Test
    void queuedCompletedStagesUseBoundedStack() {
        var bulkhead = create(config(1, 3000));
        try {
            var running = bulkhead.acquire();
            var results = new ArrayList<CompletableFuture<Integer>>();
            for (int i = 0; i < 3000; i++) {
                results.add(bulkhead.executeAsync(() -> CompletableFuture.completedFuture(1)).toCompletableFuture());
            }
            running.close();
            assertEquals(3000, results.stream().mapToInt(CompletableFuture::join).sum());
            assertEquals(0, bulkhead.inFlight());
            assertEquals(0, bulkhead.queueLength());
        } finally {
            bulkhead.release();
        }
    }

    @Test
    void aimdGrowsUnderDemandAndShrinksWithoutRevokingExistingPermits() {
        var clock = new AtomicLong();
        var bulkhead = new KoraBulkhead(
            "orders", new Config(4, 0, Duration.ZERO, BulkheadConfig.Type.AIMD, adaptive(2)), NoopBulkheadTelemetry.INSTANCE, clock::get
        );
        var first = bulkhead.acquire();
        var second = bulkhead.acquire();
        clock.addAndGet(Duration.ofMillis(10).toNanos());
        second.close();
        assertEquals(3, bulkhead.currentLimit());
        first.close(); // Stale completion must not drive another increase.
        assertEquals(3, bulkhead.currentLimit());
        var one = bulkhead.acquire();
        var two = bulkhead.acquire();
        var three = bulkhead.acquire();
        three.observeError(new IOException("overload"));
        clock.addAndGet(Duration.ofMillis(10).toNanos());
        three.close();
        assertEquals(1, bulkhead.currentLimit());
        assertEquals(2, bulkhead.inFlight());
        assertNull(bulkhead.tryAcquire());
        two.close();
        assertNull(bulkhead.tryAcquire());
        one.close();
        assertEquals(4, bulkhead.maxConcurrentCalls());
        try (var recovered = bulkhead.acquire()) {
            assertEquals(1, bulkhead.inFlight());
        }
    }

    @Test
    void adaptiveIgnoresLowDemandAndLocalCancellationAndHonorsLatencyTarget() {
        var clock = new AtomicLong();
        var bulkhead = new KoraBulkhead(
            "orders", new Config(4, 0, Duration.ZERO, BulkheadConfig.Type.AIMD, adaptive(2)), NoopBulkheadTelemetry.INSTANCE, clock::get
        );
        var quiet = bulkhead.acquire();
        clock.addAndGet(Duration.ofMillis(10).toNanos());
        quiet.close();
        assertEquals(2, bulkhead.currentLimit());
        var cancelled = bulkhead.acquire();
        cancelled.observeError(new CancellationException());
        clock.addAndGet(Duration.ofMillis(100).toNanos());
        cancelled.close();
        assertEquals(2, bulkhead.currentLimit());
        var slow = bulkhead.acquire();
        clock.addAndGet(Duration.ofMillis(100).toNanos());
        slow.close();
        assertEquals(1, bulkhead.currentLimit());
        for (int i = 0; i < 10; i++) {
            var permit = bulkhead.acquire();
            clock.addAndGet(Duration.ofMillis(10).toNanos());
            permit.close();
        }
        assertTrue(bulkhead.currentLimit() <= 4);
    }

    @Test
    void throughputProbeKeepsImprovementsAndRollsBackPlateau() {
        var parameters = new BulkheadConfig.AdaptiveConfig() {

            @Override
            public int initialLimit() {
                return 2;
            }

            @Override
            public int minimumSamples() {
                return 3;
            }

            @Override
            public Duration samplingInterval() {
                return Duration.ofMillis(10);
            }
        };
        var bulkhead = create(new Config(8, 0, Duration.ZERO, BulkheadConfig.Type.THROUGHPUT, parameters));
        var controller = (ThroughputKoraBulkhead) bulkhead.delegate();
        long interval = Duration.ofMillis(10).toNanos();
        for (int i = 0; i < 3; i++) {
            controller.sample(interval, 0, interval, 2, 2, false);
        }
        assertEquals(3, bulkhead.currentLimit()); // Baseline: 300/s, probe 3.
        for (int i = 0; i < 5; i++) {
            controller.sample(interval + interval / 2, interval, interval / 2, 3, 3, false);
        }
        controller.sample(2 * interval, interval, interval, 3, 3, false);
        assertEquals(4, bulkhead.currentLimit()); // Improved: 600/s, probe 4.
        for (int i = 0; i < 3; i++) {
            controller.sample(3 * interval, 2 * interval, interval, 4, 4, false);
        }
        assertEquals(3, bulkhead.currentLimit()); // 300/s, rollback to last useful limit.
    }

    @Test
    void everyStrategyComposesWithImmediateAndQueuedAdmission() throws Exception {
        var caller = Thread.currentThread();
        for (var type : BulkheadConfig.Type.values()) {
            for (int queue : new int[] {
                    0, 1
            }) {
                var bulkhead = create(new Config(2, queue, Duration.ofSeconds(10), type, adaptive(1)));
                try {
                    var held = new ArrayList<Bulkhead.Permit>();
                    for (int i = 0; i < bulkhead.currentLimit(); i++) {
                        held.add(bulkhead.acquire());
                    }
                    if (queue == 0) {
                        assertThrows(BulkheadFullException.class, () -> bulkhead.executeAsync(() -> {
                            fail("Rejected operation started");
                            return CompletableFuture.completedFuture(caller);
                        }));
                        held.forEach(Bulkhead.Permit::close);
                        assertSame(
                            caller,
                            bulkhead.executeAsync(() -> CompletableFuture.completedFuture(Thread.currentThread()))
                                .toCompletableFuture()
                                .join()
                        );
                    } else {
                        var result = bulkhead.executeAsync(() -> CompletableFuture.completedFuture(Thread.currentThread()))
                            .toCompletableFuture();
                        assertEquals(1, bulkhead.queueLength());
                        held.forEach(Bulkhead.Permit::close);
                        assertSame(caller, result.get(5, TimeUnit.SECONDS));
                    }
                    assertEquals(0, bulkhead.inFlight());
                } finally {
                    bulkhead.release();
                }
            }
        }
    }

    @Test
    void queuedStagePreservesSubmissionContextAndRestoresReleasingCallersContext() throws Throwable {
        var bulkhead = create(config(1, 1));
        try {
            var running = bulkhead.acquire();
            var principal = new Principal() {};
            var mdc = new MDC();
            mdc.put0("request", "orders");
            var result = ScopedValue.where(Principal.VALUE, principal).where(MDC.VALUE, mdc).call(() -> bulkhead.executeAsync(() -> {
                assertSame(principal, Principal.current());
                assertNotSame(mdc, MDC.VALUE.get());
                assertTrue(MDC.VALUE.get().values().containsKey("request"));
                return CompletableFuture.completedFuture("ok");
            }).toCompletableFuture());
            var releasingPrincipal = new Principal() {};
            ScopedValue.where(Principal.VALUE, releasingPrincipal).run(() -> {
                running.close();
                assertSame(releasingPrincipal, Principal.current());
            });
            assertEquals("ok", result.join());
            assertNull(Principal.current());
            assertEquals(0, bulkhead.inFlight());
        } finally {
            bulkhead.release();
        }
    }

    @Test
    void queuedWorkCannotInheritReleasingCallersPrincipal() {
        var bulkhead = create(config(1, 1));
        try {
            var running = bulkhead.acquire();
            var result = bulkhead.executeAsync(() -> {
                assertNull(Principal.current());
                return CompletableFuture.completedFuture("ok");
            }).toCompletableFuture();
            ScopedValue.where(Principal.VALUE, new Principal() {}).run(running::close);
            assertEquals("ok", result.join());
        } finally {
            bulkhead.release();
        }
    }

    @Test
    void queuedAsyncCancellationRetainsSourcePermitUntilTermination() throws Exception {
        var bulkhead = create(config(1, 1));
        var source = new CompletableFuture<String>();
        var started = new CountDownLatch(1);
        try {
            var running = bulkhead.acquire();
            var result = bulkhead.executeAsync(() -> {
                started.countDown();
                return source;
            }).toCompletableFuture();
            assertEquals(1, bulkhead.queueLength());
            running.close();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            result.cancel(true);
            assertEquals(1, bulkhead.inFlight());
            assertFalse(source.isCancelled());
            source.complete("ok");
            assertEquals(0, bulkhead.inFlight());
        } finally {
            source.complete("done");
            bulkhead.release();
        }
    }

    @Test
    void shutdownRejectsQueueButRetainsRunningPermit() throws Exception {
        var bulkhead = create(config(1, 1));
        var source = new CompletableFuture<String>();
        var started = new CountDownLatch(1);
        var active = bulkhead.executeAsync(() -> {
            started.countDown();
            return source;
        }).toCompletableFuture();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        var waiting = bulkhead.acquireAsync().toCompletableFuture();
        bulkhead.release();
        assertEquals(
            Reason.SHUTDOWN,
            assertInstanceOf(BulkheadFullException.class, assertThrows(CompletionException.class, waiting::join).getCause()).reason()
        );
        assertEquals(Reason.SHUTDOWN, assertThrows(BulkheadFullException.class, bulkhead::acquire).reason());
        assertEquals(1, bulkhead.inFlight());
        source.complete("done");
        assertEquals("done", active.get(5, TimeUnit.SECONDS));
        assertEquals(0, bulkhead.inFlight());
        bulkhead.release();
    }

    @Test
    void rejectsInvalidQueueAndAdaptiveSettings() {
        assertThrows(IllegalArgumentException.class, () -> create(new Config(1, 1, Duration.ZERO, BulkheadConfig.Type.FIXED, null)));
        assertThrows(IllegalArgumentException.class, () -> create(new Config(1, -1, Duration.ZERO, BulkheadConfig.Type.FIXED, null)));
        assertThrows(IllegalArgumentException.class, () -> create(new Config(1, 0, Duration.ZERO, BulkheadConfig.Type.AIMD, adaptive(2))));
    }
}
