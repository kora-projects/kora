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
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

class StripedApproxKoraCircuitBreakerTests extends Assertions {

    private static final Duration WAIT_IN_OPEN = Duration.ofMillis(50);

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
    void managerCreatesStripedApproxCircuitBreakerWhenTypeConfigured() {
        var circuitBreaker = new KoraCircuitBreaker("default", stripedConfig(4, 4, 2, 50, 2), throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);

        assertInstanceOf(StripedApproxKoraCircuitBreaker.class, circuitBreaker.delegate());
    }

    @Test
    void stripedConfigUsesDefaultStripes() {
        var config = config(true, CircuitBreakerConfig.CircuitBreakerType.STRIPED_APPROX, null, 4, 2, 50, 2);

        assertEquals(CircuitBreakerConfig.StripedApproxConfig.STRIPED_APPROX_DEFAULT_STRIPES, stripeCount(config));
    }

    @Test
    void snapshotSumsOutcomeCounters() {
        var circuitBreaker = new StripedApproxKoraCircuitBreaker(
            "default",
            stripedConfig(1, 4, 1, 100, 1),
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
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(1, 10, 8, 30, 3));

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnSuccess();
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
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(1, 2, 2, 100, 1));

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
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
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(1, 2, 2, 100, 2));

        open(circuitBreaker);

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToOpenToHalfOpenToOpen() {
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(1, 2, 2, 100, 2));

        open(circuitBreaker);

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToOpenToHalfOpenToClosed() {
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(1, 2, 2, 100, 2));

        open(circuitBreaker);

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire());
        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();

        assertTrue(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToOpenToHalfOpenToClosedComplex() {
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(1, 4, 2, 50, 2));

        open(circuitBreaker);

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire());
        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();

        assertTrue(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenCorrectlyRestoreIgnoredExceptionToOpen() {
        var circuitBreaker = new StripedApproxKoraCircuitBreaker(
            "default",
            stripedConfig(1, 4, 2, 50, 2),
            new CircuitBreakerPredicate() {
@Override
                public boolean test(Throwable throwable) {
                    return !(throwable instanceof UncheckedIOException);
                }
            },
            NoopCircuitBreakerTelemetry.INSTANCE
        );

        open(circuitBreaker);

        awaitily().dontCatchUncaughtExceptions().untilAsserted(() -> Assertions.assertDoesNotThrow(circuitBreaker::acquire));
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new UncheckedIOException(new IOException("OPS")));
        awaitily().dontCatchUncaughtExceptions().untilAsserted(() -> Assertions.assertDoesNotThrow(circuitBreaker::acquire));
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new UncheckedIOException(new IOException("OPS")));

        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        awaitily().dontCatchUncaughtExceptions().untilAsserted(() -> Assertions.assertDoesNotThrow(circuitBreaker::acquire));
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToOpenToHalfOpenToClosedForAccept() {
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(1, 4, 2, 50, 2));

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

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
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
    void switchFromClosedToOpenToHalfOpenToOpenToHalfOpenWhenPartFailToOpen() {
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(1, 2, 2, 100, 2));

        open(circuitBreaker);

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire());
        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToClosed() {
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(1, 2, 2, 100, 2));

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
    void switchFromOpenToHalfOpenAndValidateAcquireCalls() {
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(1, 1, 1, 100, 1));

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire);
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire());
    }

    @Test
    void switchFromClosedToOpenForCustomFailurePredicate() {
        var circuitBreaker = new StripedApproxKoraCircuitBreaker(
            "default",
            config(true, CircuitBreakerConfig.CircuitBreakerType.STRIPED_APPROX, striped(1), 1, 1, 100, 1),
            new CustomPredicate(),
            NoopCircuitBreakerTelemetry.INSTANCE
        );

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
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
        var circuitBreaker = new StripedApproxKoraCircuitBreaker(
            "default",
            stripedConfig(1, 1, 1, 100, 1),
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
        var circuitBreaker = stripedCircuitBreaker(config(false, CircuitBreakerConfig.CircuitBreakerType.STRIPED_APPROX, striped(1), 1, 1, 100, 1));

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
    void stripeWindowEvictsOldOutcomes() {
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(1, 2, 2, 100, 1));

        circuitBreaker.releaseOnError(new IllegalStateException());
        circuitBreaker.releaseOnSuccess();
        circuitBreaker.releaseOnSuccess();
        var snapshot = circuitBreaker.snapshot();

        assertEquals(2, snapshot.total());
        assertEquals(0, snapshot.failures());
    }

    @Test
    void stripedApproxCapacityCanBeLargerThanConfiguredWindow() {
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(4, 10, 10, 100, 1));

        for (int i = 0; i < 12; i++) {
            circuitBreaker.releaseOnSuccess();
        }

        var snapshot = circuitBreaker.snapshot();
        assertTrue(snapshot.total() <= 12);
        assertTrue(snapshot.total() <= effectiveCapacity(10, 4));
        assertEquals(0, snapshot.failures());
    }

    @Test
    void concurrentClosedSuccessRecordingStaysWithinEffectiveCapacity() throws Exception {
        var stripes = 8;
        var windowSize = 64;
        var circuitBreaker = stripedCircuitBreaker(stripedConfig(stripes, windowSize, windowSize, 100, 1));
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
        assertTrue(snapshot.total() > 0);
        assertTrue(snapshot.total() <= effectiveCapacity(windowSize, stripes));
        assertEquals(0, snapshot.failures());
    }

    @Test
    void configValidationRejectsTooLargeStripedWindowForConfiguredStripes() {
        var config = config(null, CircuitBreakerConfig.CircuitBreakerType.STRIPED_APPROX, striped(1), 0x1_0000L, 1, 100, 1);

        assertThrows(IllegalArgumentException.class, () -> CircuitBreakerConfig.validate("default", config));
    }

    @Test
    void configValidationRejectsTooManyStripes() {
        var config = config(null, CircuitBreakerConfig.CircuitBreakerType.STRIPED_APPROX, striped(CircuitBreakerConfig.StripedApproxConfig.STRIPED_APPROX_MAX_STRIPES + 1), 4, 1, 100, 1);

        assertThrows(IllegalArgumentException.class, () -> CircuitBreakerConfig.validate("default", config));
    }

    private static void open(StripedApproxKoraCircuitBreaker circuitBreaker) {
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertFalse(circuitBreaker.tryAcquire());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    private static StripedApproxKoraCircuitBreaker stripedCircuitBreaker(CircuitBreakerConfig config) {
        return new StripedApproxKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);
    }

    private static CircuitBreakerConfig stripedConfig(int stripes, long windowSize, long minimumRequiredCalls, int failureRateThreshold, int permittedCallsInHalfOpenState) {
        return config(true, CircuitBreakerConfig.CircuitBreakerType.STRIPED_APPROX, striped(stripes), windowSize, minimumRequiredCalls, failureRateThreshold, permittedCallsInHalfOpenState);
    }

    private static CircuitBreakerConfig config(Boolean enabled,
                                                           CircuitBreakerConfig.CircuitBreakerType type,
                                                           CircuitBreakerConfig.StripedApproxConfig stripedApprox,
                                                           long windowSize,
                                                           long minimumRequiredCalls,
                                                           int failureRateThreshold,
                                                           int permittedCallsInHalfOpenState) {
        return new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(enabled == null || enabled, type, countBased(windowSize, stripedApprox), null, failureRateThreshold, WAIT_IN_OPEN, permittedCallsInHalfOpenState, minimumRequiredCalls, null);
    }

    private static CircuitBreakerConfig.CountBasedConfig countBased(Long windowSize, CircuitBreakerConfig.StripedApproxConfig stripedApprox) {
        return new $CircuitBreakerConfig_CountBasedConfig_ConfigValueMapper.CountBasedConfig_Impl(windowSize, stripedApprox);
    }

    private static CircuitBreakerConfig.StripedApproxConfig striped(int stripes) {
        return new $CircuitBreakerConfig_StripedApproxConfig_ConfigValueMapper.StripedApproxConfig_Impl(stripes);
    }

    private static int stripeCount(CircuitBreakerConfig config) {
        var stripedApprox = config.countBased().stripedApprox();
        return stripedApprox == null ? CircuitBreakerConfig.StripedApproxConfig.STRIPED_APPROX_DEFAULT_STRIPES : stripedApprox.stripes();
    }

    private static int effectiveCapacity(int windowSize, int stripes) {
        var stripeWindowSize = (windowSize + stripes - 1) / stripes;
        return stripeWindowSize * stripes;
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



