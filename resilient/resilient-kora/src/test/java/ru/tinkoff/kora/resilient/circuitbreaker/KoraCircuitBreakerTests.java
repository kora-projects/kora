package ru.tinkoff.kora.resilient.circuitbreaker;

import jakarta.annotation.Nonnull;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker.State;

import java.time.Duration;

class KoraCircuitBreakerTests extends Assertions {

    private static final Duration WAIT_IN_OPEN = Duration.ofMillis(50);

    static class CustomPredicate implements CircuitBreakerPredicate {

        @Nonnull
        @Override
        public String name() {
            return "custom";
        }

        @Override
        public boolean test(@Nonnull Throwable throwable) {
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
