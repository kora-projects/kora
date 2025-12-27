package ru.tinkoff.kora.resilient.circuitbreaker;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker.State;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

class KoraCircuitBreakerTests extends Assertions {

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
        return Awaitility.await().atMost(Duration.ofMillis(150)).pollDelay(Duration.ofMillis(5));
    }

    @Test
    void switchFromClosedToOpen() {
        // given
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            true, 30, WAIT_IN_OPEN, 3, 10L, 8L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

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
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            true, 100, WAIT_IN_OPEN, 1, 2L, 2L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

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
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            true, 100, WAIT_IN_OPEN, 2, 2L, 2L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

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
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            true, 100, WAIT_IN_OPEN, 2, 2L, 2L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

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
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            true, 100, WAIT_IN_OPEN, 2, 2L, 2L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

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
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            true, 50, WAIT_IN_OPEN, 2, 4L, 2L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

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
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            true, 50, WAIT_IN_OPEN, 2, 4L, 2L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new CircuitBreakerPredicate() {
            @Override
            public String name() {
                return "kora";
            }

            @Override
            public boolean test(Throwable throwable) {
                return !(throwable instanceof UncheckedIOException);
            }
        }, new NoopCircuitBreakerMetrics());

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
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            true, 50, WAIT_IN_OPEN, 2, 4L, 2L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

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
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            true, 100, WAIT_IN_OPEN, 2, 2L, 2L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

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
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            true, 100, WAIT_IN_OPEN, 2, 2L, 2L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

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
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            true, 100, WAIT_IN_OPEN, 1, 1L, 1L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

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
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            true, 100, WAIT_IN_OPEN, 1, 1L, 1L, "custom");
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new CustomPredicate(), new NoopCircuitBreakerMetrics());

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
}
