package io.koraframework.resilient.circuitbreaker;

import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation.CallAcquireStatus;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation.CallResult;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetry;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * CircuitBreaker - striped approximate count window implementation.
 * <p>
 * CLOSED-state statistics are intentionally approximate: every stripe owns a local ring and local counters,
 * and global snapshot is the sum of stripe snapshots. Under uneven thread distribution the effective global
 * window can be shifted and does not represent a strict FIFO of last N calls. The state machine remains strict
 * and atomic.
 * <p>
 * This is the fastest hot-path implementation for high concurrency: writes avoid a global sequencer and
 * update only one stripe. The trade-off is approximate statistics instead of exact global count-based order.
 */
@SuppressWarnings("ConstantConditions")
final class StripedApproxKoraCircuitBreaker implements CircuitBreaker {

    private static final long HALF_OPEN_COUNTER_MASK = 0xFFFFL;
    private static final long CLOSED_STATE = 1L << 63;
    private static final long HALF_OPEN_STATE = 1L << 62;
    private static final long HALF_OPEN_INCREMENT_SUCCESS = 1L << 16;
    private static final long HALF_OPEN_INCREMENT_ERROR = 1L << 32;

    private static final int OUTCOME_EMPTY = 0;
    private static final int OUTCOME_SUCCESS = 1;
    private static final int OUTCOME_FAILURE = 2;
    private static final int OUTCOME_IGNORED = 3;
    private static final int OUTCOME_SLOW = 4;

    private static final int COUNTER_BITS = 16;
    private static final long COUNTER_MASK = 0xFFFFL;
    private static final int FAILURE_SHIFT = 16;
    private static final int SLOW_SHIFT = 32;
    private static final int IGNORED_SHIFT = 48;

    private final AtomicLong state = new AtomicLong(CLOSED_STATE);
    private final String name;
    private final CircuitBreakerConfig config;
    private final CircuitBreakerPredicate failurePredicate;
    private final CircuitBreakerTelemetry telemetry;
    private final long waitDurationInOpenStateInNanos;
    private final LongSupplier currentTimeNanos;
    private final long startedNanos;
    private final Stripe[] stripes;
    private final int stripeMask;

    StripedApproxKoraCircuitBreaker(String name, CircuitBreakerConfig config, CircuitBreakerPredicate failurePredicate, CircuitBreakerTelemetry telemetry) {
        this(name, config, failurePredicate, telemetry, System::nanoTime);
    }

    StripedApproxKoraCircuitBreaker(String name, CircuitBreakerConfig config, CircuitBreakerPredicate failurePredicate, CircuitBreakerTelemetry telemetry, LongSupplier currentTimeNanos) {
        this.name = name;
        this.config = config;
        this.failurePredicate = failurePredicate;
        this.telemetry = telemetry;
        this.waitDurationInOpenStateInNanos = toNanosSaturated(config.waitDurationInOpenState());
        this.currentTimeNanos = currentTimeNanos;
        this.startedNanos = currentTimeNanos.getAsLong();

        var stripedApprox = config.countBased().stripedApprox();
        var configuredStripes = stripedApprox == null
            ? CircuitBreakerConfig.StripedApproxConfig.STRIPED_APPROX_DEFAULT_STRIPES
            : stripedApprox.stripes();
        var stripeCount = Math.min(configuredStripes, Math.toIntExact(config.countBased().windowSize()));
        stripeCount = nextPowerOfTwo(stripeCount);
        this.stripes = new Stripe[stripeCount];
        this.stripeMask = stripeCount - 1;
        var stripeWindowSize = Math.toIntExact((config.countBased().windowSize() + stripeCount - 1) / stripeCount);
        for (int i = 0; i < stripeCount; i++) {
            this.stripes[i] = new Stripe(stripeWindowSize);
        }
    }

    State getState() {
        if (Boolean.FALSE.equals(config.enabled())) {
            return State.CLOSED;
        } else {
            return getState(state.get());
        }
    }

    Snapshot snapshot() {
        long total = 0;
        long failures = 0;
        long slowCalls = 0;
        long ignored = 0;
        for (Stripe localStripe : this.stripes) {
            var counters = localStripe.counters.get();
            total += total(counters);
            failures += failures(counters);
            slowCalls += slowCalls(counters);
            ignored += ignored(counters);
        }
        return new Snapshot(total, failures, slowCalls, ignored);
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
            var result = supplier.get();
            releaseOnSuccess();
            return result;
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

    private int countHalfOpenSuccess(long value) {
        return (int) ((value >> 16) & HALF_OPEN_COUNTER_MASK);
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
        if (beenInOpenState >= waitDurationInOpenStateInNanos) {
            if (this.state.compareAndSet(stateLong, HALF_OPEN_STATE + 1)) {
                onStateChange(State.OPEN, State.HALF_OPEN, null, observation);
                observation.recordCallAcquire(State.HALF_OPEN, CallAcquireStatus.PERMITTED);
                return true;
            } else {
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

            final long currentStateLong = state.get();
            final State currentState = getState(currentStateLong);
            if (currentState == State.CLOSED) {
                stripe().record(OUTCOME_SUCCESS);
                observation.recordCallResult(State.CLOSED, CallResult.SUCCESS);
                return;
            }

            State prevState;
            State newState;
            while (true) {
                final long observedStateLong = state.get();
                final long newStateLong = calculateStateOnSuccess(observedStateLong);
                if (state.compareAndSet(observedStateLong, newStateLong)) {
                    prevState = getState(observedStateLong);
                    newState = getState(newStateLong);
                    break;
                }
            }
            if (prevState != newState) {
                clearWindow();
                onStateChange(prevState, newState, null, observation);
            }
            observation.recordCallResult(prevState, CallResult.SUCCESS);
        } finally {
            observation.end();
        }
    }

    private long calculateStateOnSuccess(long currentState) {
        final State state = getState(currentState);
        if (state == State.HALF_OPEN) {
            final int success = countHalfOpenSuccess(currentState) + 1;
            final int permitted = config.permittedCallsInHalfOpenState();
            if (success >= permitted) {
                return CLOSED_STATE;
            }

            return currentState + HALF_OPEN_INCREMENT_SUCCESS;
        } else {
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
                if (getState(state.get()) == State.CLOSED) {
                    stripe().record(OUTCOME_IGNORED);
                }
                observation.recordCallResult(getState(state.get()), CallResult.IGNORED_FAILURE);
                return;
            }

            final long currentStateLong = state.get();
            final State currentState = getState(currentStateLong);
            if (currentState == State.CLOSED) {
                stripe().record(OUTCOME_FAILURE);
                if (shouldOpen()) {
                    openFromClosed(observation, throwable);
                } else {
                    observation.observeError(throwable);
                }
                observation.recordCallResult(State.CLOSED, CallResult.FAILURE);
                return;
            }

            State prevState;
            State newState;
            while (true) {
                final long observedStateLong = state.get();
                final long newStateLong = calculateStateOnFailure(observedStateLong);
                if (state.compareAndSet(observedStateLong, newStateLong)) {
                    prevState = getState(observedStateLong);
                    newState = getState(newStateLong);
                    break;
                }
            }

            if (prevState != newState) {
                clearWindow();
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
        if (state == State.HALF_OPEN) {
            return getOpenState();
        } else {
            return currentState;
        }
    }

    private boolean shouldOpen() {
        long total = 0;
        long failures = 0;
        for (Stripe localStripe : this.stripes) {
            var counters = localStripe.counters.get();
            total += total(counters);
            failures += failures(counters);
        }
        if (total < config.minimumRequiredCalls()) {
            return false;
        }

        return (int) (failures * 100 / total) >= config.failureRateThreshold();
    }

    private void openFromClosed(CircuitBreakerObservation observation, Throwable throwable) {
        final long currentStateLong = state.get();
        if (getState(currentStateLong) == State.CLOSED && state.compareAndSet(currentStateLong, getOpenState())) {
            clearWindow();
            onStateChange(State.CLOSED, State.OPEN, throwable, observation);
        } else {
            observation.observeError(throwable);
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

    private void clearWindow() {
        for (Stripe localStripe : this.stripes) {
            localStripe.clear();
        }
    }

    private Stripe stripe() {
        return this.stripes[stripeIndex()];
    }

    private int stripeIndex() {
        var x = (int) Thread.currentThread().threadId();
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        return x & stripeMask;
    }

    private void recordFallback(CallNotPermittedException exception) {
        var observation = this.telemetry.observe();
        try {
            observation.recordCallResult(exception.state(), CallResult.FALLBACK);
        } finally {
            observation.end();
        }
    }

    private static long packedDelta(int outcome) {
        return switch (outcome) {
            case OUTCOME_SUCCESS -> 1L;
            case OUTCOME_FAILURE -> 1L | (1L << FAILURE_SHIFT);
            case OUTCOME_IGNORED -> 1L << IGNORED_SHIFT;
            case OUTCOME_SLOW -> 1L | (1L << SLOW_SHIFT);
            default -> 0L;
        };
    }

    private static long total(long counters) {
        return counters & COUNTER_MASK;
    }

    private static long failures(long counters) {
        return (counters >>> FAILURE_SHIFT) & COUNTER_MASK;
    }

    private static long slowCalls(long counters) {
        return (counters >>> SLOW_SHIFT) & COUNTER_MASK;
    }

    private static long ignored(long counters) {
        return (counters >>> IGNORED_SHIFT) & COUNTER_MASK;
    }

    private static int nextPowerOfTwo(int value) {
        var highest = Integer.highestOneBit(value);
        return highest == value ? value : highest << 1;
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    record Snapshot(long total, long failures, long slowCalls, long ignored) {}

    private static final class Stripe {

        private final AtomicInteger cursor = new AtomicInteger();
        private final AtomicIntegerArray outcomes;
        private final AtomicLong counters = new AtomicLong();

        private Stripe(int windowSize) {
            this.outcomes = new AtomicIntegerArray(windowSize);
        }

        private void record(int outcome) {
            var position = cursor.getAndIncrement();
            var slot = (position & Integer.MAX_VALUE) % outcomes.length();
            var previous = outcomes.getAndSet(slot, outcome);
            counters.addAndGet(packedDelta(outcome) - packedDelta(previous));
        }

        private void clear() {
            for (int i = 0; i < outcomes.length(); i++) {
                outcomes.set(i, OUTCOME_EMPTY);
            }
            counters.set(0);
            cursor.set(0);
        }
    }
}
