package io.koraframework.resilient.circuitbreaker;

import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException;
import io.koraframework.resilient.circuitbreaker.telemetry.impl.NoopCircuitBreakerTelemetry;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

class TimeBasedKoraCircuitBreakerTests extends Assertions {

    private static final Duration WAIT_IN_OPEN = Duration.ofMillis(50);
    private static final Duration WINDOW = Duration.ofSeconds(1);

    @NullMarked
    static class CustomPredicate implements CircuitBreakerPredicate {
@Override
        public boolean test(Throwable throwable) {
            return throwable instanceof IllegalStateException;
        }
    }

    private static ConditionFactory awaitily() {
        return Awaitility.await().atMost(Duration.ofSeconds(1)).pollDelay(Duration.ofMillis(5));
    }

    @Test
    void managerCreatesTimeBasedCircuitBreakerWhenTypeConfigured() {
        var circuitBreaker = new KoraCircuitBreaker("default", config(WINDOW, 4, 2, 50, 2), throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);

        assertInstanceOf(TimeBasedKoraCircuitBreaker.class, circuitBreaker.delegate());
    }

    @Test
    void timeBasedConfigUsesDefaults() {
        var config = config(true, WINDOW, 16, 2, 50, 2, CircuitBreakerConfig.TimeBasedCounterType.ATOMIC);

        assertEquals(CircuitBreakerConfig.TimeBasedConfig.TIME_BASED_DEFAULT_SAMPLE_COUNT, config.timeBased().sampleCount());
        assertEquals(CircuitBreakerConfig.TimeBasedConfig.TIME_BASED_DEFAULT_COUNTER_STRIPES, config.timeBased().counterStripes());
        assertEquals(CircuitBreakerConfig.TimeBasedCounterType.ATOMIC, config.timeBased().counterType());
    }

    @Test
    void snapshotSumsOutcomeCounters() {
        var circuitBreaker = new TimeBasedKoraCircuitBreaker(
            "default",
            config(WINDOW, 4, 1, 100, 1),
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
    void snapshotDropsExpiredBuckets() {
        var ticker = new AtomicLong();
        var circuitBreaker = new TimeBasedKoraCircuitBreaker(
            "default",
            config(Duration.ofMillis(100), 2, 1, 100, 1),
            throwable -> true,
            NoopCircuitBreakerTelemetry.INSTANCE,
            ticker::get
        );

        circuitBreaker.releaseOnSuccess();
        assertEquals(1, circuitBreaker.snapshot().total());
        assertEquals(0, circuitBreaker.snapshot().failures());

        ticker.addAndGet(Duration.ofMillis(150).toNanos());
        circuitBreaker.releaseOnSuccess();
        var snapshot = circuitBreaker.snapshot();

        assertEquals(1, snapshot.total());
        assertEquals(0, snapshot.failures());
    }

    @Test
    void switchFromClosedToOpen() {
        var circuitBreaker = timeBased(config(WINDOW, 4, 8, 30, 3));

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
        var circuitBreaker = timeBased(config(WINDOW, 2, 2, 100, 1));

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
        var circuitBreaker = timeBased(config(WINDOW, 2, 2, 100, 2));

        open(circuitBreaker);

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToClosed() {
        var circuitBreaker = timeBased(config(WINDOW, 2, 2, 100, 2));

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
        var circuitBreaker = new TimeBasedKoraCircuitBreaker(
            "default",
            config(WINDOW, 4, 2, 50, 2),
            new CircuitBreakerPredicate() {
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
        var circuitBreaker = timeBased(config(WINDOW, 4, 2, 50, 2));
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
        var circuitBreaker = new TimeBasedKoraCircuitBreaker(
            "default",
            config(true, WINDOW, 1, 1, 100, 1),
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
        var circuitBreaker = new TimeBasedKoraCircuitBreaker(
            "default",
            config(WINDOW, 1, 1, 100, 1),
            throwable -> true,
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
        var circuitBreaker = timeBased(config(false, WINDOW, 1, 1, 100, 1));

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
    void longAdderCounterTypeRecordsOutcomes() {
        var circuitBreaker = timeBased(config(WINDOW, 4, 1, 100, 1, CircuitBreakerConfig.TimeBasedCounterType.LONG_ADDER));

        circuitBreaker.releaseOnSuccess();
        circuitBreaker.releaseOnError(new IllegalStateException());
        var snapshot = circuitBreaker.snapshot();

        assertEquals(2, snapshot.total());
        assertEquals(1, snapshot.failures());
    }

    @Test
    void concurrentClosedSuccessRecordingDoesNotAllocateWindowPerCall() throws Exception {
        var circuitBreaker = timeBased(config(Duration.ofSeconds(5), 16, 1, 100, 1));
        var threads = 8;
        var iterations = 2000;
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var executor = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < iterations; j++) {
                            circuitBreaker.releaseOnSuccess();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
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

        var snapshot = circuitBreaker.snapshot();
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals((long) threads * iterations, snapshot.total());
        assertEquals(0, snapshot.failures());
    }

    private static void open(TimeBasedKoraCircuitBreaker circuitBreaker) {
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    private static TimeBasedKoraCircuitBreaker timeBased(CircuitBreakerConfig config) {
        return new TimeBasedKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);
    }

    private static CircuitBreakerConfig config(Duration windowDuration,
                                                           int sampleCount,
                                                           long minimumRequiredCalls,
                                                           int failureRateThreshold,
                                                           int permittedCallsInHalfOpenState) {
        return config(true, windowDuration, sampleCount, minimumRequiredCalls, failureRateThreshold, permittedCallsInHalfOpenState);
    }

    private static CircuitBreakerConfig config(Duration windowDuration,
                                                           int sampleCount,
                                                           long minimumRequiredCalls,
                                                           int failureRateThreshold,
                                                           int permittedCallsInHalfOpenState,
                                                           CircuitBreakerConfig.TimeBasedCounterType counterType) {
        return config(true, windowDuration, sampleCount, minimumRequiredCalls, failureRateThreshold, permittedCallsInHalfOpenState, counterType);
    }

    private static CircuitBreakerConfig config(Boolean enabled,
                                                           Duration windowDuration,
                                                           int sampleCount,
                                                           long minimumRequiredCalls,
                                                           int failureRateThreshold,
                                                           int permittedCallsInHalfOpenState) {
        return config(enabled, windowDuration, sampleCount, minimumRequiredCalls, failureRateThreshold, permittedCallsInHalfOpenState, CircuitBreakerConfig.TimeBasedCounterType.ATOMIC);
    }

    private static CircuitBreakerConfig config(Boolean enabled,
                                                           Duration windowDuration,
                                                           int sampleCount,
                                                           long minimumRequiredCalls,
                                                           int failureRateThreshold,
                                                           int permittedCallsInHalfOpenState,
                                                           CircuitBreakerConfig.TimeBasedCounterType counterType) {
        return config(enabled, windowDuration, sampleCount, minimumRequiredCalls, failureRateThreshold, permittedCallsInHalfOpenState, CircuitBreakerConfig.TimeBasedConfig.TIME_BASED_DEFAULT_COUNTER_STRIPES, counterType);
    }

    private static CircuitBreakerConfig config(Boolean enabled,
                                                           Duration windowDuration,
                                                           int sampleCount,
                                                           long minimumRequiredCalls,
                                                           int failureRateThreshold,
                                                           int permittedCallsInHalfOpenState,
                                                           int counterStripes,
                                                           CircuitBreakerConfig.TimeBasedCounterType counterType) {
        return new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(enabled == null || enabled,
            CircuitBreakerConfig.CircuitBreakerType.TIME_BASED,
            null,
            timeBased(windowDuration, sampleCount, counterStripes, counterType),
            failureRateThreshold,
            WAIT_IN_OPEN,
            permittedCallsInHalfOpenState,
            minimumRequiredCalls,
            null
        );
    }

    private static CircuitBreakerConfig.TimeBasedConfig timeBased(Duration windowDuration,
                                                                  int sampleCount,
                                                                  int counterStripes,
                                                                  CircuitBreakerConfig.TimeBasedCounterType counterType) {
        return new $CircuitBreakerConfig_TimeBasedConfig_ConfigValueMapper.TimeBasedConfig_Impl(windowDuration, sampleCount, counterStripes, counterType);
    }

    private static CircuitBreakerPredicate ignoredPredicate() {
        return new CircuitBreakerPredicate() {
            @Override
            public boolean test(Throwable throwable) {
                return false;
            }
        };
    }
}



