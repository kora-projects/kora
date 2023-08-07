package ru.tinkoff.kora.resilient.circuitbreaker;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker.State;

import javax.annotation.Nonnull;
import java.time.Duration;

class KoraCircuitBreakerTests extends Assertions {

    private static final Duration WAIT_IN_OPEN = Duration.ofMillis(10);

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
            30, WAIT_IN_OPEN, 3, 10L, 8L, KoraCircuitBreakerPredicate.class.getCanonicalName());
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
        assertEquals(State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire()); // open
    }

    @Test
    void switchFromClosedToOpenForMinimumNumberOfCalls() {
        // given
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            100, WAIT_IN_OPEN, 1, 2L, 2L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());

        // then
        assertEquals(State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire()); // open
    }


    @Test
    void switchFromOpenToHalfOpenToOpen() {
        // given
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            100, WAIT_IN_OPEN, 1, 1L, 1L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertEquals(State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire()); // open

        // then
        awaitily().until(circuitBreaker::tryAcquire); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertEquals(State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire()); // open
    }

    @Test
    void switchFromOpenToHalfOpenToClosed() {
        // given
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            100, WAIT_IN_OPEN, 1, 1L, 1L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertEquals(State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire()); // open

        // then
        awaitily().until(circuitBreaker::tryAcquire); // half open
        assertEquals(State.HALF_OPEN, circuitBreaker.getState());
        circuitBreaker.releaseOnSuccess();
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
    }

    @Test
    void switchFromOpenToHalfOpenAndValidateAcquireCalls() {
        // given
        final CircuitBreakerConfig.NamedConfig config = new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            100, WAIT_IN_OPEN, 1, 1L, 1L, KoraCircuitBreakerPredicate.class.getCanonicalName());
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new KoraCircuitBreakerPredicate(), new NoopCircuitBreakerMetrics());

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());
        assertEquals(State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire()); // open

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
            100, WAIT_IN_OPEN, 1, 1L, 1L, "custom");
        final KoraCircuitBreaker circuitBreaker = new KoraCircuitBreaker("default", config, new CustomPredicate(), new NoopCircuitBreakerMetrics());

        // when
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new NullPointerException());
        assertEquals(State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.tryAcquire()); // closed
        circuitBreaker.releaseOnError(new IllegalStateException());

        // then
        assertEquals(State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.tryAcquire()); // open
    }
}
