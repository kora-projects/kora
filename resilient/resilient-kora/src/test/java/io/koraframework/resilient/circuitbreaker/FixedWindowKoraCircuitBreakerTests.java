package io.koraframework.resilient.circuitbreaker;

import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetry;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetryConfig;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import io.koraframework.resilient.circuitbreaker.CircuitBreaker.State;
import io.koraframework.resilient.circuitbreaker.telemetry.impl.NoopCircuitBreakerTelemetry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

class FixedWindowKoraCircuitBreakerTests extends Assertions {

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
    void switchFromClosedToOpen() {
        // given
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(10L, null), null, 30, WAIT_IN_OPEN, 3, 8L, null);
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertEquals(State.CLOSED, circuitBreaker.getState());

        // then
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertFalse(circuitBreaker.tryAcquire()); // closed switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenForMinimumNumberOfCalls() {
        // given
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(2L, null), null, 100, WAIT_IN_OPEN, 1, 2L, null);
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());

        // then
        assertFalse(circuitBreaker.tryAcquire()); // closed switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToOpen() {
        // given
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(2L, null), null, 100, WAIT_IN_OPEN, 2, 2L, null);
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire()); // open
        assertEquals(State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        // then
        assertFalse(circuitBreaker.tryAcquire()); // half open switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToOpenToHalfOpenToOpen() {
        // given
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(2L, null), null, 100, WAIT_IN_OPEN, 2, 2L, null);
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire()); // closed switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire()); // half open switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        // then
        assertFalse(circuitBreaker.tryAcquire()); // half open switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToOpenToHalfOpenToClosed() {
        // given
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(2L, null), null, 100, WAIT_IN_OPEN, 2, 2L, null);
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire()); // closed switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire()); // // half open switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker::tryAcquire); // half open
        assertFalse(circuitBreaker::tryAcquire); // half open limit
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();

        // then
        assertTrue(circuitBreaker.tryAcquire()); // closed
        assertEquals(State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToOpenToHalfOpenToClosedComplex() {
        // given
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(4L, null), null, 50, WAIT_IN_OPEN, 2, 2L, null);
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire()); // closed switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire()); // // half open switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire); // open switched to half open + 1 acquire
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker::tryAcquire); // half open + 1 acquire
        assertFalse(circuitBreaker::tryAcquire); // half open limit reached (3rd call)
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();

        // then
        assertTrue(circuitBreaker.tryAcquire()); // closed
        assertEquals(State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenCorrectlyRestoreIgnoredExceptionToOpen() {
        // given
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(4L, null), null, 50, WAIT_IN_OPEN, 2, 2L, null);
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, new CircuitBreakerPredicate() {
@Override
            public boolean test(Throwable throwable) {
                return !(throwable instanceof UncheckedIOException);
            }
        }, NoopCircuitBreakerTelemetry.INSTANCE);

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire()); // closed switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());

        awaitily().dontCatchUncaughtExceptions().untilAsserted(() -> Assertions.assertDoesNotThrow(circuitBreaker::acquire)); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new UncheckedIOException(new IOException("OPS")));
        awaitily().dontCatchUncaughtExceptions().untilAsserted(() -> Assertions.assertDoesNotThrow(circuitBreaker::acquire)); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new UncheckedIOException(new IOException("OPS")));

        // then
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        awaitily().dontCatchUncaughtExceptions().untilAsserted(() -> Assertions.assertDoesNotThrow(circuitBreaker::acquire)); // half open
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertEquals(State.OPEN, circuitBreaker.getState()); // half open switched to open
        assertFalse(circuitBreaker.tryAcquire());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToOpenToHalfOpenToClosedForAccept() {
        // given
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(4L, null), null, 50, WAIT_IN_OPEN, 2, 2L, null);
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);

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

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertThrows(IllegalStateException.class, () -> circuitBreaker.accept(failSupplier));
        assertThrows(IllegalStateException.class, () -> circuitBreaker.accept(failSupplier));

        assertThrows(CallNotPermittedException.class, () -> circuitBreaker.accept(failSupplier)); // closed switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());

        awaitily().until(successCallable); // open switched to half open + 1 acquire
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());

        assertThrows(IllegalStateException.class, () -> circuitBreaker.accept(failSupplier)); // half open switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());

        // then
        awaitily().until(successCallable); // open switched to half open + 1 acquire
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        awaitily().until(successCallable); // half open switched to CLOSED
        assertEquals(State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToOpenToHalfOpenWhenPartFailToOpen() {
        // given
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(2L, null), null, 100, WAIT_IN_OPEN, 2, 2L, null);
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire()); // closed switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        assertFalse(circuitBreaker.tryAcquire()); // half open switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker::tryAcquire); // half open
        assertFalse(circuitBreaker::tryAcquire); // half open limit
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        // then
        assertFalse(circuitBreaker.tryAcquire()); // open
        assertEquals(State.OPEN, circuitBreaker.getState());
    }

    @Test
    void switchFromClosedToOpenToHalfOpenToClosed() {
        // given
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(2L, null), null, 100, WAIT_IN_OPEN, 2, 2L, null);
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertFalse(circuitBreaker.tryAcquire()); // open
        assertEquals(State.OPEN, circuitBreaker.getState());

        awaitily().until(circuitBreaker::tryAcquire); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();
        assertTrue(circuitBreaker.tryAcquire()); // half open
        assertFalse(circuitBreaker.tryAcquire()); // half open limit
        circuitBreaker.releaseOnSuccess();

        // then
        assertTrue(circuitBreaker.tryAcquire()); // closed
        assertEquals(State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void switchFromOpenToHalfOpenAndValidateAcquireCalls() {
        // given
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(1L, null), null, 100, WAIT_IN_OPEN, 1, 1L, null);
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE);

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertFalse(circuitBreaker.tryAcquire()); // open
        assertEquals(State.OPEN, circuitBreaker.getState());

        // then
        awaitily().until(circuitBreaker::tryAcquire); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire()); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
    }

    @Test
    void switchFromClosedToOpenForCustomFailurePredicate() {
        // given
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(1L, null), null, 100, WAIT_IN_OPEN, 1, 1L, null);
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, new CustomPredicate(), NoopCircuitBreakerTelemetry.INSTANCE);

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new NullPointerException());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        assertEquals(State.CLOSED, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());

        // then
        assertFalse(circuitBreaker.tryAcquire()); // closed switched to open
        assertEquals(State.OPEN, circuitBreaker.getState());
    }

    @Test
    void openToHalfOpenUsesMonotonicTicker() {
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(1L, null), null, 100, WAIT_IN_OPEN, 1, 1L, null);
        var ticker = new AtomicLong();
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, throwable -> true, NoopCircuitBreakerTelemetry.INSTANCE, ticker::get);

        assertTrue(circuitBreaker.tryAcquire());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertEquals(State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire());

        ticker.addAndGet(WAIT_IN_OPEN.toNanos());

        assertTrue(circuitBreaker.tryAcquire());
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
    }

    @Test
    void configValidationRejectsFixedWindowCounterOverflow() {
        var config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(0x8000_0000L, null), null, 100, WAIT_IN_OPEN, 1, 1L, null);

        assertThrows(IllegalArgumentException.class, () -> CircuitBreakerConfig.validate("default", config));
    }

    @Test
    void configValidationRejectsHalfOpenCounterOverflow() {
        var config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(1L, null), null, 100, WAIT_IN_OPEN, 0x1_0000, 1L, null);

        assertThrows(IllegalArgumentException.class, () -> CircuitBreakerConfig.validate("default", config));
    }

    @Test
    void telemetryRecordsCallResults() {
        final CircuitBreakerConfig config = new $CircuitBreakerConfig_ConfigValueMapper.CircuitBreakerConfig_Impl(
            true, CircuitBreakerConfig.CircuitBreakerType.FIXED_WINDOW, countBased(1L, null), null, 100, WAIT_IN_OPEN, 1, 1L, null);
        var telemetry = new CountingTelemetry();
        final FixedWindowKoraCircuitBreaker circuitBreaker = new FixedWindowKoraCircuitBreaker("default", config, throwable -> true, telemetry);

        assertEquals("ok", circuitBreaker.accept(() -> "ok"));
        assertThrows(IllegalStateException.class, () -> circuitBreaker.accept(() -> {
            throw new IllegalStateException();
        }));
        assertEquals("fallback", circuitBreaker.accept(() -> "ignored", () -> "fallback"));

        assertTrue(telemetry.results.contains(CircuitBreakerObservation.CallResult.SUCCESS));
        assertTrue(telemetry.results.contains(CircuitBreakerObservation.CallResult.FAILURE));
        assertTrue(telemetry.results.contains(CircuitBreakerObservation.CallResult.FALLBACK));
    }

    private static CircuitBreakerConfig.CountBasedConfig countBased(Long windowSize, CircuitBreakerConfig.StripedApproxConfig stripedApprox) {
        return new $CircuitBreakerConfig_CountBasedConfig_ConfigValueMapper.CountBasedConfig_Impl(windowSize, stripedApprox);
    }

    private static final class CountingTelemetry implements CircuitBreakerTelemetry {

        private final ArrayList<CircuitBreakerObservation.CallResult> results = new ArrayList<>();

        @Override
        public CircuitBreakerObservation observe() {
            return new CircuitBreakerObservation() {
                @Override
                public void recordCallAcquire(State state, CallAcquireStatus callStatus) {}

                @Override
                public void recordCallResult(State state, CallResult callResult) {
                    results.add(callResult);
                }

                @Override
                public void recordStateChange(State previousState, State newState) {}

                @Override
                public Span span() {
                    return Span.getInvalid();
                }

                @Override
                public void end() {}

                @Override
                public void observeError(Throwable e) {}
            };
        }
    }
}



