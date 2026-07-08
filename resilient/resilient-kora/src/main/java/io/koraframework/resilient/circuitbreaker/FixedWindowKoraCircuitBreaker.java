package io.koraframework.resilient.circuitbreaker;

import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetry;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation.CallAcquireStatus;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation.CallResult;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * CircuitBreaker - Fixed Window implementation
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
final class FixedWindowKoraCircuitBreaker implements CircuitBreaker {

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
    private final CircuitBreakerTelemetry telemetry;
    private final long waitDurationInOpenStateInNanos;
    private final LongSupplier currentTimeNanos;
    private final long startedNanos;

    FixedWindowKoraCircuitBreaker(String name, CircuitBreakerConfig.NamedConfig config, CircuitBreakerPredicate failurePredicate, CircuitBreakerTelemetry telemetry) {
        this(name, config, failurePredicate, telemetry, System::nanoTime);
    }

    FixedWindowKoraCircuitBreaker(String name, CircuitBreakerConfig.NamedConfig config, CircuitBreakerPredicate failurePredicate, CircuitBreakerTelemetry telemetry, LongSupplier currentTimeNanos) {
        this.state = new AtomicLong(CLOSED_STATE);
        this.name = name;
        this.config = config;
        this.failurePredicate = failurePredicate;
        this.telemetry = telemetry;
        this.waitDurationInOpenStateInNanos = toNanosSaturated(config.waitDurationInOpenState());
        this.currentTimeNanos = currentTimeNanos;
        this.startedNanos = currentTimeNanos.getAsLong();
    }

    State getState() {
        if (Boolean.FALSE.equals(config.enabled())) {
            return State.CLOSED;
        } else {
            return getState(state.get());
        }
    }

    @Override
    public <T> T accept(Supplier<T> callable) {
        return internalAccept(callable, null);
    }

    @Override
    public <T> T accept(Supplier<T> callable, Supplier<T> fallback) {
        return internalAccept(callable, fallback);
    }

    private <T> T internalAccept(Supplier<T> supplier, @Nullable Supplier<T> fallback) {
        if (Boolean.FALSE.equals(config.enabled())) {
            var observation = this.telemetry.observe();
            try {
                observation.recordCallAcquire(State.CLOSED, CallAcquireStatus.DISABLED);
                var result = supplier.get();
                observation.recordCallResult(State.CLOSED, CallResult.SUCCESS);
                return result;
            } catch (Throwable e) {
                observation.observeError(e);
                observation.recordCallResult(State.CLOSED, CallResult.FAILURE);
                throw e;
            } finally {
                observation.end();
            }
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
            recordFallback(e);
        } catch (Throwable e) {
            releaseOnError(e);
            throw e;
        }

        return fallback.get();
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

    private int countHalfOpenSuccess(long value) {
        return (int) ((value >> 16) & HALF_OPEN_COUNTER_MASK);
    }

    private int countHalfOpenError(long value) {
        return (int) ((value >> 32) & HALF_OPEN_COUNTER_MASK);
    }

    private int countHalfOpenAcquired(long value) {
        return (int) (value & HALF_OPEN_COUNTER_MASK);
    }

    private long getOpenState() {
        return currentElapsedNanos();
    }

    private long currentElapsedNanos() {
        return Math.max(0, currentTimeNanos.getAsLong() - startedNanos);
    }

    private void onStateChange(State prevState,
                               State newState,
                               @Nullable Throwable throwable,
                               CircuitBreakerObservation observation) {
        if (throwable != null) {
            observation.observeError(throwable);
        }
        observation.recordStateChange(prevState, newState);
    }

    @Override
    public void acquire() throws CallNotPermittedException {
        if (!tryAcquire()) {
            throw new CallNotPermittedException(getState(state.get()), name);
        }
    }

    @Override
    public boolean tryAcquire() {
        var observation = this.telemetry.observe();
        try {
            return tryAcquire(observation);
        } catch (Throwable e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    private boolean tryAcquire(CircuitBreakerObservation observation) {
        if (Boolean.FALSE.equals(config.enabled())) {
            observation.recordCallAcquire(State.CLOSED, CallAcquireStatus.DISABLED);
            return true;
        }

        final long stateLong = state.get();
        final State state = getState(stateLong);
        if (state == State.CLOSED) {
            observation.recordCallAcquire(state, CallAcquireStatus.PERMITTED);
            return true;
        }

        if (state == State.HALF_OPEN) {
            final int acquired = countHalfOpenAcquired(stateLong);
            if (acquired < config.permittedCallsInHalfOpenState()) {
                final boolean isAcquired = this.state.compareAndSet(stateLong, stateLong + 1);
                if (isAcquired) {
                    observation.recordCallAcquire(state, CallAcquireStatus.PERMITTED);
                    return true;
                } else {
                    return tryAcquire(observation);
                }
            } else {
                observation.recordCallAcquire(state, CallAcquireStatus.REJECTED);
                return false;
            }
        }

        final long currentTimeInNanos = currentElapsedNanos();
        final long beenInOpenState = currentTimeInNanos - stateLong;
        // go to half open
        if (beenInOpenState >= waitDurationInOpenStateInNanos) {
            if (this.state.compareAndSet(stateLong, HALF_OPEN_STATE + 1)) {
                onStateChange(State.OPEN, State.HALF_OPEN, null, observation);
                observation.recordCallAcquire(State.HALF_OPEN, CallAcquireStatus.PERMITTED);
                return true;
            } else {
                // prob concurrently switched to half open and have to reacquire
                return tryAcquire(observation);
            }
        } else {
            observation.recordCallAcquire(state, CallAcquireStatus.REJECTED);
            return false;
        }
    }

    @Override
    public void releaseOnSuccess() {
        var observation = this.telemetry.observe();
        try {
            if (Boolean.FALSE.equals(config.enabled())) {
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
                onStateChange(prevState, newState, null, observation);
            }
            observation.recordCallResult(prevState, CallResult.SUCCESS);
        } finally {
            observation.end();
        }
    }

    private long calculateStateOnSuccess(long currentState) {
        final State state = getState(currentState);
        if (state == State.CLOSED) {
            final int total = countClosedTotal(currentState) + 1;
            if (total == config.windowSize()) {
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
    public void releaseOnError(Throwable throwable) {
        var observation = this.telemetry.observe();
        try {
            if (Boolean.FALSE.equals(config.enabled())) {
                return;
            }

            if (!failurePredicate.test(throwable)) {
                releaseIgnoredError();
                observation.recordCallResult(getState(state.get()), CallResult.IGNORED_FAILURE);
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
                onStateChange(prevState, newState, throwable, observation);
            } else {
                observation.observeError(throwable);
            }
            observation.recordCallResult(prevState, CallResult.FAILURE);
        } finally {
            observation.end();
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
            } else if (total == config.windowSize()) {
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

    private void releaseIgnoredError() {
        while (true) {
            final long currentStateLong = state.get();
            final State currentState = getState(currentStateLong);
            if (currentState != State.HALF_OPEN) {
                return;
            }

            if (state.compareAndSet(currentStateLong, currentStateLong - 1)) {
                return;
            }
        }
    }

    private void recordFallback(CallNotPermittedException exception) {
        var observation = this.telemetry.observe();
        try {
            observation.recordCallResult(exception.state(), CallResult.FALLBACK);
        } finally {
            observation.end();
        }
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
