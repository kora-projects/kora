package ru.tinkoff.kora.resilient.circuitbreaker;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.time.Clock;
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
final class KoraCircuitBreaker implements CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(KoraCircuitBreaker.class);

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

    private final AtomicLong state;
    private final String name;
    private final CircuitBreakerConfig.NamedConfig config;
    private final CircuitBreakerPredicate failurePredicate;
    private final CircuitBreakerMetrics metrics;
    private final long waitDurationInOpenStateInMillis;
    private final Clock clock;

    KoraCircuitBreaker(String name, CircuitBreakerConfig.NamedConfig config, CircuitBreakerPredicate failurePredicate, CircuitBreakerMetrics metrics) {
        this.state = new AtomicLong(CLOSED_STATE);
        this.name = name;
        this.config = config;
        this.failurePredicate = failurePredicate;
        this.metrics = metrics;
        this.waitDurationInOpenStateInMillis = config.waitDurationInOpenState().toMillis();
        this.clock = Clock.systemDefaultZone();

        this.metrics.recordState(name, State.CLOSED);
    }

    @Nonnull
    State getState() {
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("CircuitBreaker '{}' is disabled", name);
            return State.CLOSED;
        } else {
            return getState(state.get());
        }
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
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("CircuitBreaker '{}' is disabled", name);
            return supplier.get();
        }

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

    private void onStateChange(@Nonnull State prevState, @Nonnull State newState) {
        logger.info("CircuitBreaker '{}' switched from {} to {}", name, prevState, newState);
        metrics.recordState(name, newState);
    }

    @Override
    public void acquire() throws CallNotPermittedException {
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("CircuitBreaker '{}' is disabled", name);
            return;
        }

        if (!tryAcquire()) {
            throw new CallNotPermittedException(getState(state.get()), name);
        }
    }

    @Override
    public boolean tryAcquire() {
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("CircuitBreaker '{}' is disabled", name);
            return true;
        }

        final long value = state.get();
        final State state = getState(value);
        if (state == State.CLOSED) {
            logger.trace("CircuitBreaker '{}' acquired in CLOSED state", name);
            return true;
        }

        if (state == State.HALF_OPEN) {
            final short acquired = countHalfOpenAcquired(value);
            if (acquired < config.permittedCallsInHalfOpenState()) {
                final boolean isAcquired = this.state.compareAndSet(value, value + 1);
                if (isAcquired) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("CircuitBreaker '{}' acquired in HALF_OPEN state with {} calls left", name, acquired - 1);
                    }
                    return true;
                } else {
                    return tryAcquire();
                }
            } else {
                logger.trace("CircuitBreaker '{}' rejected in HALF_OPEN state due to all {} calls acquired", name, acquired);
                return false;
            }
        }

        final long currentTimeInMillis = clock.millis();
        final long beenInOpenState = currentTimeInMillis - value;
        // go to half open
        if (beenInOpenState >= waitDurationInOpenStateInMillis) {
            if (this.state.compareAndSet(value, HALF_OPEN_STATE + 1)) {
                onStateChange(State.OPEN, State.HALF_OPEN);
                if (logger.isTraceEnabled()) {
                    logger.trace("CircuitBreaker '{}' acquired in HALF_OPEN state with {} calls left",
                        name, config.permittedCallsInHalfOpenState() - 1);
                }
                return true;
            } else {
                // prob concurrently switched to half open and have to reacquire
                return tryAcquire();
            }
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("CircuitBreaker '{}' rejected in OPEN state for waiting '{}' when require minimum wait time '{}'",
                    name, TimeUtils.durationForLogging(beenInOpenState), TimeUtils.durationForLogging(waitDurationInOpenStateInMillis));
            }
            return false;
        }
    }

    @Override
    public void releaseOnSuccess() {
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("CircuitBreaker '{}' is disabled", name);
            return;
        }

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

        if (prevState == newState) {
            logger.trace("CircuitBreaker '{}' released in {} state on success", name, newState);
        } else {
            logger.trace("CircuitBreaker '{}' released from {} to {} state on success", name, prevState, newState);
        }
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

            return currentState + HALF_OPEN_INCREMENT_SUCCESS;
        } else {
            //do nothing with open state
            return currentState;
        }
    }

    @Override
    public void releaseOnError(@Nonnull Throwable throwable) {
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("CircuitBreaker '{}' is disabled", name);
            return;
        }

        if (!failurePredicate.test(throwable)) {
            if (logger.isTraceEnabled()) {
                final long currentStateLong = state.get();
                var currentState = getState(currentStateLong);
                logger.trace("CircuitBreaker '{}' skipped error in {} state due to predicate test failed: {}", name, currentState, throwable.toString());
            }
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

        if (prevState == newState) {
            logger.trace("CircuitBreaker '{}' released in {} state on error: {}", name, newState, throwable.toString());
        } else {
            logger.trace("CircuitBreaker '{}' released from {} to {} state on error: {}", name, prevState, newState, throwable.toString());
        }
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
            // if any error in half-open then go to open state
            return getOpenState();
        } else {
            // do nothing with open state
            return currentState;
        }
    }
}
