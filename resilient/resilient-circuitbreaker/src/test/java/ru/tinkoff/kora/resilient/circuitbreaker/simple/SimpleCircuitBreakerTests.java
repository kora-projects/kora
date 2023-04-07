package ru.tinkoff.kora.resilient.circuitbreaker.simple;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker.State;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerFailurePredicate;

import javax.annotation.Nonnull;
import java.time.Duration;

class SimpleCircuitBreakerTests extends Assertions {

    private static final Duration WAIT_IN_OPEN = Duration.ofMillis(10);

    static class CustomFailurePredicate implements CircuitBreakerFailurePredicate {

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
        final SimpleCircuitBreakerConfig.NamedConfig config = new SimpleCircuitBreakerConfig.NamedConfig(
            30, WAIT_IN_OPEN, 3, 10L, 8L, SimpleCircuitBreakerFailurePredicate.class.getCanonicalName());
        final SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker("default", config, new SimpleCircuitBreakerFailurePredicate(), new NoopCircuitBreakerMetrics());

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
        final SimpleCircuitBreakerConfig.NamedConfig config = new SimpleCircuitBreakerConfig.NamedConfig(
            100, WAIT_IN_OPEN, 1, 2L, 2L, SimpleCircuitBreakerFailurePredicate.class.getCanonicalName());
        final SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker("default", config, new SimpleCircuitBreakerFailurePredicate(), new NoopCircuitBreakerMetrics());

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
        final SimpleCircuitBreakerConfig.NamedConfig config = new SimpleCircuitBreakerConfig.NamedConfig(
            100, WAIT_IN_OPEN, 1, 1L, 1L, SimpleCircuitBreakerFailurePredicate.class.getCanonicalName());
        final SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker("default", config, new SimpleCircuitBreakerFailurePredicate(), new NoopCircuitBreakerMetrics());

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
        final SimpleCircuitBreakerConfig.NamedConfig config = new SimpleCircuitBreakerConfig.NamedConfig(
            100, WAIT_IN_OPEN, 1, 1L, 1L, SimpleCircuitBreakerFailurePredicate.class.getCanonicalName());
        final SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker("default", config, new SimpleCircuitBreakerFailurePredicate(), new NoopCircuitBreakerMetrics());

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
        final SimpleCircuitBreakerConfig.NamedConfig config = new SimpleCircuitBreakerConfig.NamedConfig(
            100, WAIT_IN_OPEN, 1, 1L, 1L, SimpleCircuitBreakerFailurePredicate.class.getCanonicalName());
        final SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker("default", config, new SimpleCircuitBreakerFailurePredicate(), new NoopCircuitBreakerMetrics());

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
        final SimpleCircuitBreakerConfig.NamedConfig config = new SimpleCircuitBreakerConfig.NamedConfig(
            100, WAIT_IN_OPEN, 1, 1L, 1L, "custom");
        final SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker("default", config, new CustomFailurePredicate(), new NoopCircuitBreakerMetrics());

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
