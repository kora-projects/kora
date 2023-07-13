package ru.tinkoff.kora.resilient.circuitbreaker.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerFailurePredicate;
import ru.tinkoff.kora.resilient.circuitbreaker.telemetry.CircuitBreakerMetrics;

import javax.annotation.Nonnull;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * --------------------------------------------------------------------------------------------------
 * Closed {@link #state}
 * 10 | 0000000000000000000000000000000 | 0000000000000000000000000000000
 * ^                     ^                              ^
 * state sign     errors count (31 bits)      request count (31 bits)
 * <p>
 * Open {@link #state}
 * 00 | 00000000000000000000000000000000000000000000000000000000000000
 * ^                                    ^
 * state sign             start time of open state (millis)
 * <p>
 * Half open {@link #state}
 * 01 | 00000000000000 | 0000000000000000 | 0000000000000000 | 0000000000000000
 * ^                           ^                   ^                  ^
 * state sign     error count (16 bit)   success count (16 bits)   acquired count (16 bits)
 * --------------------------------------------------------------------------------------------------
 */
@SuppressWarnings("ConstantConditions")
record SimpleCircuitBreaker(
    AtomicLong state,
    String name,
    SimpleCircuitBreakerConfig.NamedConfig config,
    CircuitBreakerFailurePredicate failurePredicate,
    CircuitBreakerMetrics metrics,
    long waitDurationInOpenStateInMillis,
    Clock clock
) implements CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(SimpleCircuitBreaker.class);

    private static final long CLOSED_COUNTER_MASK = 0x7FFF_FFFFL;
    private static final long CLOSED_STATE = 1L << 63;
    private static final long HALF_OPEN_COUNTER_MASK = 0xFFFFL;
    private static final long HALF_OPEN_STATE = 1L << 62;
    private static final long HALF_OPEN_INCREMENT_SUCCESS = 1L << 16;
    private static final long HALF_OPEN_INCREMENT_ERROR = 1L << 32;
    private static final long OPEN_STATE = 0;

    private static final long COUNTER_INC = 1L;
    private static final long ERR_COUNTER_INC = 1L << 31;
    private static final long BOTH_COUNTERS_INC = ERR_COUNTER_INC + COUNTER_INC;

    SimpleCircuitBreaker(String name, SimpleCircuitBreakerConfig.NamedConfig config, CircuitBreakerFailurePredicate failurePredicate, CircuitBreakerMetrics metrics) {
        this(new AtomicLong(CLOSED_STATE), name, config, failurePredicate, metrics, config.waitDurationInOpenState().toMillis(), Clock.systemDefaultZone());
        this.metrics.recordState(name, State.CLOSED);
    }

    @Nonnull
    State getState() {
        return getState(state.get());
    }

    @Override
    public <T> T accept(@Nonnull Supplier<T> callable) {
        return internalAccept(callable, null);
    }

    @Override
    public <T> T accept(@Nonnull Supplier<T> callable, @Nonnull Supplier<T> fallback) {
        return internalAccept(callable, fallback);
    }

    private <T> T internalAccept(@Nonnull Supplier<T> supplier, Supplier<T> fallback) {
        try {
            acquire();
            var t = supplier.get();
            releaseOnSuccess();
            return t;
        } catch (CallNotPermittedException e) {
            if (fallback == null) {
                throw e;
            }

            return fallback.get();
        } catch (Exception e) {
            releaseOnError(e);
            throw e;
        }
    }

    private State getState(long value) {
        return switch ((int) (value >> 62 & 0x03)) {
            case 0 -> State.OPEN;
            case 1 -> State.HALF_OPEN;
            default -> State.CLOSED;
        };
    }

    private int countClosedErrors(long value) {
        return (int) ((value >> 31) & CLOSED_COUNTER_MASK);
    }

    private int countClosedTotal(long value) {
        return (int) (value & CLOSED_COUNTER_MASK);
    }

    private short countHalfOpenSuccess(long value) {
        return (short) ((value >> 16) & HALF_OPEN_COUNTER_MASK);
    }

    private short countHalfOpenError(long value) {
        return (short) ((value >> 32) & HALF_OPEN_COUNTER_MASK);
    }

    private short countHalfOpenAcquired(long value) {
        return (short) (value & HALF_OPEN_COUNTER_MASK);
    }

    private long getOpenState() {
        return clock.millis();
    }

    void onStateChange(@Nonnull State prevState, @Nonnull State newState) {
        logger.debug("CircuitBreaker '{}' switched from {} to {}", name, prevState, newState);
        metrics.recordState(name, newState);
    }

    @Override
    public void acquire() throws CallNotPermittedException {
        if (!tryAcquire()) {
            throw new CallNotPermittedException(getState(state.get()), name);
        }
    }

    @Override
    public boolean tryAcquire() {
        final long value = state.get();
        final State state = getState(value);
        if (state == State.CLOSED) {
            logger.trace("CircuitBreaker '{}' acquired", name);
            return true;
        }

        if (state == State.HALF_OPEN) {
            final short acquired = countHalfOpenAcquired(value);
            if (acquired < config.permittedCallsInHalfOpenState()) {
                final boolean isAcquired = this.state.compareAndSet(value, value + 1);
                if (isAcquired) {
                    logger.trace("CircuitBreaker '{}' acquired", name);
                } else {
                    return tryAcquire();
                }
            } else {
                logger.trace("CircuitBreaker '{}' can't be acquired in HALF_OPEN state", name);
                return false;
            }
        }

        // go to half open
        final long currentTimeInMillis = clock.millis();
        final long beenInOpenState = currentTimeInMillis - value;
        if (beenInOpenState >= waitDurationInOpenStateInMillis) {
            if (this.state.compareAndSet(value, HALF_OPEN_STATE + 1)) {
                onStateChange(State.OPEN, State.HALF_OPEN);
                logger.trace("CircuitBreaker '{}' acquired", name);
                return true;
            } else {
                // prob concurrently switched to half open and have to reacquire
                return tryAcquire();
            }
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("CircuitBreaker '{}' can't be acquired being in OPEN state for '{}' when require minimum '{}'",
                    name, Duration.ofMillis(beenInOpenState), Duration.ofMillis(waitDurationInOpenStateInMillis));
            }
            return false;
        }
    }

    @Override
    public void releaseOnSuccess() {
        State prevState;
        State newState;
        while (true) {
            final long currentStateLong = state.get();
            final long newStateLong = calculateStateOnSuccess(currentStateLong);
            if (state.compareAndSet(currentStateLong, newStateLong)) {
                newState = getState(newStateLong);
                prevState = getState(currentStateLong);
                break;
            }
        }

        if (prevState != newState) {
            onStateChange(prevState, newState);
        }

        logger.trace("CircuitBreaker '{}' released on success", name);
    }

    private long calculateStateOnSuccess(long currentState) {
        final State state = getState(currentState);
        if (state == State.CLOSED) {
            final int total = countClosedTotal(currentState) + 1;
            if (total == config.slidingWindowSize()) {
                return CLOSED_STATE;
            } else {
                // just increase counter
                return currentState + COUNTER_INC;
            }
        } else if (state == State.HALF_OPEN) {
            final int success = countHalfOpenSuccess(currentState) + 1;
            final int permitted = config.permittedCallsInHalfOpenState();
            if (success >= permitted) {
                return CLOSED_STATE;
            }

            final int errors = countHalfOpenError(currentState);
            final int total = success + errors;
            if (total >= permitted) {
                return getOpenState();
            }

            return currentState + HALF_OPEN_INCREMENT_SUCCESS;
        } else {
            //do nothing with open state
            return currentState;
        }
    }

    @Override
    public void releaseOnError(@Nonnull Throwable throwable) {
        if (!failurePredicate.test(throwable)) {
            return;
        }

        State prevState;
        State newState;
        while (true) {
            final long currentStateLong = state.get();
            final long newStateLong = calculateStateOnFailure(currentStateLong);
            if (state.compareAndSet(currentStateLong, newStateLong)) {
                newState = getState(newStateLong);
                prevState = getState(currentStateLong);
                break;
            }
        }

        if (prevState != newState) {
            onStateChange(prevState, newState);
        }

        logger.trace("CircuitBreaker '{}' released on error: {}", name, throwable.getClass().getCanonicalName());
    }

    private long calculateStateOnFailure(long currentState) {
        final State state = getState(currentState);
        if (state == State.CLOSED) {
            final int total = countClosedTotal(currentState) + 1;
            if (total < config.minimumRequiredCalls()) {
                // just increase both counters
                return currentState + BOTH_COUNTERS_INC;
            }

            final float errors = countClosedErrors(currentState) + 1;
            final int failureRatePercentage = (int) (errors / total * 100);
            if (failureRatePercentage >= config.failureRateThreshold()) {
                return getOpenState();
            } else if (total == config.slidingWindowSize()) {
                return CLOSED_STATE;
            } else {
                // just increase both counters
                return currentState + BOTH_COUNTERS_INC;
            }
        } else if (state == State.HALF_OPEN) {
            return getOpenState();
        } else {
            // do nothing with open state
            return currentState;
        }
    }
}
