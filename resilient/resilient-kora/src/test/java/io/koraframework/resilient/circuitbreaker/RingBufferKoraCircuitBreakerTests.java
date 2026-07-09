package io.koraframework.resilient.circuitbreaker;

import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetryConfig;
import io.koraframework.resilient.circuitbreaker.telemetry.impl.NoopCircuitBreakerTelemetry;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

class RingBufferKoraCircuitBreakerTests extends Assertions {

    private static final Duration WAIT_IN_OPEN = Duration.ofMillis(50);

    @NullMarked
    static class CustomPredicate implements CircuitBreakerPredicate {

        @Override
        public String name() {
            return "custom";
        }

        @Override
        public boolean test(Throwable throwable) {
            return throwable instanceof IllegalStateException;
        }
    }

    private static ConditionFactory awaitily() {
        return Awaitility.await().atMost(Duration.ofSeconds(1)).pollDelay(Duration.ofMillis(5));
    }

    @Test
    void managerCreatesRingBufferCircuitBreakerWhenTypeConfigured() {
        var manager = new KoraCircuitBreakerManager(
            circuitBreakerConfig(config(4, 2, 50, 2)),
            List.of(new KoraCircuitBreakerPredicate()),
            (name, telemetryConfig) -> NoopCircuitBreakerTelemetry.INSTANCE
        );

        assertInstanceOf(RingBufferKoraCircuitBreaker.class, manager.get(CircuitBreakerConfig.DEFAULT));
    }

    @Test
    void snapshotSumsOutcomeCounters() {
        var circuitBreaker = new RingBufferKoraCircuitBreaker(
            "default",
            config(4, 1, 100, 1),
            ignoredPredicate(),
            NoopCircuitBreakerTelemetry.INSTANCE
        );

        circuitBreaker.releaseOnSuccess();
        circuitBreaker.releaseOnError(new IllegalStateException());
        var snapshot = circuitBreaker.snapshot();

        assertEquals(1, snapshot.total());
        assertEquals(0, snapshot.failures());
        assertEquals(1, snapshot.ignored());
    }

    @Test
    void switchFromClosedToOpen() {
        var circuitBreaker = ringBuffer(config(10, 8, 30, 3));

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        for (int i = 0; i < 7; i++) {
            assertTrue(circuitBreaker.tryAcquire());
            circuitBreaker.releaseOnSuccess();
        }
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenForMinimumNumberOfCalls() {
        var circuitBreaker = ringBuffer(config(2, 2, 100, 1));

        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToOpen() {
        var circuitBreaker = ringBuffer(config(2, 2, 100, 2));

        open(circuitBreaker);

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToClosed() {
        var circuitBreaker = ringBuffer(config(2, 2, 100, 2));

        open(circuitBreaker);

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire());
        assertFalse(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnSuccess();

        assertTrue(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenCorrectlyRestoreIgnoredExceptionToOpen() {
        var circuitBreaker = new RingBufferKoraCircuitBreaker(
            "default",
            config(4, 2, 50, 2),
            new CircuitBreakerPredicate() {
                @Override
                public String name() {
                    return "kora";
                }

                @Override
                public boolean test(Throwable throwable) {
                    return !(throwable instanceof UncheckedIOException);
                }
            },
            NoopCircuitBreakerTelemetry.INSTANCE
        );

        open(circuitBreaker);

        awaitily().dontCatchUncaughtExceptions().untilAsserted(() -> assertDoesNotThrow(circuitBreaker::acquire));
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new UncheckedIOException(new IOException("OPS")));
        awaitily().dontCatchUncaughtExceptions().untilAsserted(() -> assertDoesNotThrow(circuitBreaker::acquire));
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new UncheckedIOException(new IOException("OPS")));

        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        awaitily().dontCatchUncaughtExceptions().untilAsserted(() -> assertDoesNotThrow(circuitBreaker::acquire));
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToOpenToHalfOpenToClosedForAccept() {
        var circuitBreaker = ringBuffer(config(4, 2, 50, 2));
        Callable<Boolean> successCallable = () -> {
            try {
                return circuitBreaker.accept(() -> "success") != null;
            } catch (CallNotPermittedException e) {
                return false;
            }
        };
        Supplier<Object> failSupplier = () -> {
            if (true) {
                throw new IllegalStateException();
            }
            return null;
        };

        assertThrows(IllegalStateException.class, () -> circuitBreaker.accept(failSupplier));
        assertThrows(IllegalStateException.class, () -> circuitBreaker.accept(failSupplier));
        assertThrows(CallNotPermittedException.class, () -> circuitBreaker.accept(failSupplier));
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        awaitily().until(successCallable);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        assertThrows(IllegalStateException.class, () -> circuitBreaker.accept(failSupplier));
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        awaitily().until(successCallable);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        awaitily().until(successCallable);
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenForCustomFailurePredicate() {
        var circuitBreaker = new RingBufferKoraCircuitBreaker(
            "default",
            config(true, 1, 1, 100, 1, "custom"),
            new CustomPredicate(),
            NoopCircuitBreakerTelemetry.INSTANCE
        );

        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new NullPointerException());
        assertTrue(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void openToHalfOpenUsesMonotonicTicker() {
        var ticker = new AtomicLong();
        var circuitBreaker = new RingBufferKoraCircuitBreaker(
            "default",
            config(1, 1, 100, 1),
            new KoraCircuitBreakerPredicate(),
            NoopCircuitBreakerTelemetry.INSTANCE,
            ticker::get
        );

        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire());

        ticker.addAndGet(WAIT_IN_OPEN.toNanos());

        assertTrue(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
    }

    @Test
    void disabledCircuitBreakerAlwaysPermitsCalls() {
        var circuitBreaker = ringBuffer(config(false, 1, 1, 100, 1));

        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertThrows(IllegalStateException.class, () -> circuitBreaker.accept(() -> {
            throw new IllegalStateException();
        }));
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void ringBufferEvictsOldOutcomesExactly() {
        var circuitBreaker = ringBuffer(config(2, 2, 100, 1));

        circuitBreaker.releaseOnError(new IllegalStateException());
        circuitBreaker.releaseOnSuccess();
        circuitBreaker.releaseOnSuccess();
        var snapshot = circuitBreaker.snapshot();

        assertEquals(2, snapshot.total());
        assertEquals(0, snapshot.failures());
    }

    @Test
    void concurrentClosedSuccessRecordingStaysWithinWindow() throws Exception {
        var windowSize = 64;
        var circuitBreaker = ringBuffer(config(windowSize, windowSize, 100, 1));
        var threads = 8;
        var iterations = 2000;
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var failures = new ArrayList<Throwable>();
        var executor = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < iterations; j++) {
                            circuitBreaker.releaseOnSuccess();
                        }
                    } catch (Throwable e) {
                        synchronized (failures) {
                            failures.add(e);
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertTrue(failures.isEmpty(), () -> failures.toString());
        var snapshot = circuitBreaker.snapshot();
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(windowSize, snapshot.total());
        assertEquals(0, snapshot.failures());
    }

    private static void open(RingBufferKoraCircuitBreaker circuitBreaker) {
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    private static RingBufferKoraCircuitBreaker ringBuffer(CircuitBreakerConfig.NamedConfig config) {
        return new RingBufferKoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), NoopCircuitBreakerTelemetry.INSTANCE);
    }

    private static CircuitBreakerConfig.NamedConfig config(long windowSize, long minimumRequiredCalls, int failureRateThreshold, int permittedCallsInHalfOpenState) {
        return config(true, windowSize, minimumRequiredCalls, failureRateThreshold, permittedCallsInHalfOpenState);
    }

    private static CircuitBreakerConfig.NamedConfig config(Boolean enabled, long windowSize, long minimumRequiredCalls, int failureRateThreshold, int permittedCallsInHalfOpenState) {
        return config(enabled, windowSize, minimumRequiredCalls, failureRateThreshold, permittedCallsInHalfOpenState, KoraCircuitBreakerPredicate.class.getCanonicalName());
    }

    private static CircuitBreakerConfig.NamedConfig config(Boolean enabled,
                                                           long windowSize,
                                                           long minimumRequiredCalls,
                                                           int failureRateThreshold,
                                                           int permittedCallsInHalfOpenState,
                                                           String failurePredicateName) {
        return new $CircuitBreakerConfig_NamedConfig_ConfigValueMapper.NamedConfig_Impl(
            enabled, CircuitBreakerConfig.CircuitBreakerType.RING_BUFFER, countBased(windowSize, null), null, failureRateThreshold, WAIT_IN_OPEN, permittedCallsInHalfOpenState, minimumRequiredCalls, failurePredicateName
        );
    }

    private static CircuitBreakerConfig circuitBreakerConfig(CircuitBreakerConfig.NamedConfig config) {
        return new TestCircuitBreakerConfig(Map.of(CircuitBreakerConfig.DEFAULT, config));
    }

    private static CircuitBreakerPredicate ignoredPredicate() {
        return new CircuitBreakerPredicate() {
            @Override
            public String name() {
                return "ignored";
            }

            @Override
            public boolean test(Throwable throwable) {
                return false;
            }
        };
    }

    private static CircuitBreakerConfig.CountBasedConfig countBased(Long windowSize, CircuitBreakerConfig.StripedApproxConfig stripedApprox) {
        return new $CircuitBreakerConfig_CountBasedConfig_ConfigValueMapper.CountBasedConfig_Impl(windowSize, stripedApprox);
    }

    private record TestCircuitBreakerConfig(Map<String, CircuitBreakerConfig.NamedConfig> circuitbreaker) implements CircuitBreakerConfig {
        @Override
        public CircuitBreakerTelemetryConfig telemetry() {
            return null;
        }
    }
}
