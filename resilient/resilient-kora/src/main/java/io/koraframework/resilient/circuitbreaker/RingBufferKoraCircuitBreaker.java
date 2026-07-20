package io.koraframework.resilient.circuitbreaker;

import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation.CallAcquireStatus;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation.CallResult;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetry;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * CircuitBreaker - exact count-based sequenced ring buffer implementation.
 * <p>
 * CLOSED-state statistics use one global completion sequence and per-slot CAS. This preserves a global
 * order for completed calls without locks, synchronized blocks, or a global CAS loop around the whole ring.
 * Counters are maintained incrementally and can be briefly eventually consistent while a completion owns a
 * sequence but has not yet published its slot. The state machine itself remains strict and atomic.
 * <p>
 * This is the strongest count-based implementation: the CLOSED window tracks the globally latest N completion
 * events by sequence. It is more precise than {@link StripedApproxKoraCircuitBreaker}, but the global sequencer
 * and slot CAS make the hot path more expensive under very high contention.
 */
@SuppressWarnings("ConstantConditions")
final class RingBufferKoraCircuitBreaker implements CircuitBreaker {

    private static final long HALF_OPEN_COUNTER_MASK = 0xFFFFL;
    private static final long CLOSED_STATE = 1L << 63;
    private static final long HALF_OPEN_STATE = 1L << 62;
    private static final long HALF_OPEN_INCREMENT_SUCCESS = 1L << 16;

    private static final int OUTCOME_EMPTY = 0;
    private static final int OUTCOME_SUCCESS = 1;
    private static final int OUTCOME_FAILURE = 2;
    private static final int OUTCOME_IGNORED = 8;

    private static final long OUTCOME_MASK = 0xFFL;
    private static final long SEQUENCE_MASK = 0xFFFF_FFFFFFL;
    private static final int SEQUENCE_SHIFT = 8;
    private static final int EPOCH_SHIFT = 48;

    private final AtomicLong state = new AtomicLong(CLOSED_STATE);
    private final String name;
    private final CircuitBreakerConfig.NamedConfig config;
    private final CircuitBreakerPredicate failurePredicate;
    private final CircuitBreakerTelemetry telemetry;
    private final long waitDurationInOpenStateInNanos;
    private final LongSupplier currentTimeNanos;
    private final long startedNanos;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong windowEpoch = new AtomicLong(2);
    private final AtomicLongArray ringSlots;
    private final int windowSize;
    private final AtomicInteger bufferedCalls = new AtomicInteger();
    private final AtomicInteger failedCalls = new AtomicInteger();
    private final AtomicInteger slowCalls = new AtomicInteger();
    private final AtomicInteger ignoredCalls = new AtomicInteger();

    RingBufferKoraCircuitBreaker(String name, CircuitBreakerConfig.NamedConfig config, CircuitBreakerPredicate failurePredicate, CircuitBreakerTelemetry telemetry) {
        this(name, config, failurePredicate, telemetry, System::nanoTime);
    }

    RingBufferKoraCircuitBreaker(String name, CircuitBreakerConfig.NamedConfig config, CircuitBreakerPredicate failurePredicate, CircuitBreakerTelemetry telemetry, LongSupplier currentTimeNanos) {
        this.name = name;
        this.config = config;
        this.failurePredicate = failurePredicate;
        this.telemetry = telemetry;
        this.waitDurationInOpenStateInNanos = toNanosSaturated(config.waitDurationInOpenState());
        this.currentTimeNanos = currentTimeNanos;
        this.startedNanos = currentTimeNanos.getAsLong();
        this.windowSize = Math.toIntExact(config.countBased().windowSize());
        this.ringSlots = new AtomicLongArray(this.windowSize);
    }

    State getState() {
        if (Boolean.FALSE.equals(config.enabled())) {
            return State.CLOSED;
        } else {
            return getState(state.get());
        }
    }

    Snapshot snapshot() {
        return new Snapshot(
            bufferedCalls.get(),
            failedCalls.get(),
            slowCalls.get(),
            ignoredCalls.get()
        );
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
                clearWindow();
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
                record(OUTCOME_SUCCESS);
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
                    record(OUTCOME_IGNORED);
                }
                observation.recordCallResult(getState(state.get()), CallResult.IGNORED_FAILURE);
                return;
            }

            final long currentStateLong = state.get();
            final State currentState = getState(currentStateLong);
            if (currentState == State.CLOSED) {
                record(OUTCOME_FAILURE);
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

    private boolean record(int outcome) {
        final long fullEpoch = stableEpoch();
        final long slotEpoch = slotEpoch(fullEpoch);
        final long seq = sequence.getAndIncrement() & SEQUENCE_MASK;
        final int slot = (int) (seq % windowSize);
        final long newPacked = pack(slotEpoch, seq, outcome);

        while (windowEpoch.get() == fullEpoch) {
            final long oldPacked = ringSlots.get(slot);
            final long oldEpoch = epoch(oldPacked);
            if (oldEpoch == slotEpoch && sequence(oldPacked) >= seq) {
                return false;
            }

            if (ringSlots.compareAndSet(slot, oldPacked, newPacked)) {
                if (windowEpoch.get() != fullEpoch) {
                    ringSlots.compareAndSet(slot, newPacked, 0);
                    return false;
                }

                final int oldOutcome = oldEpoch == slotEpoch ? outcome(oldPacked) : OUTCOME_EMPTY;
                applyDelta(oldOutcome, outcome);
                return true;
            }
        }

        return false;
    }

    private long stableEpoch() {
        while (true) {
            final long epoch = windowEpoch.get();
            if ((epoch & 1L) == 0) {
                return epoch;
            }
            Thread.onSpinWait();
        }
    }

    private void applyDelta(int oldOutcome, int newOutcome) {
        final int totalDelta = totalDelta(newOutcome) - totalDelta(oldOutcome);
        if (totalDelta != 0) {
            bufferedCalls.addAndGet(totalDelta);
        }

        final int failureDelta = failureDelta(newOutcome) - failureDelta(oldOutcome);
        if (failureDelta != 0) {
            failedCalls.addAndGet(failureDelta);
        }

        final int slowDelta = slowDelta(newOutcome) - slowDelta(oldOutcome);
        if (slowDelta != 0) {
            slowCalls.addAndGet(slowDelta);
        }

        final int ignoredDelta = ignoredDelta(newOutcome) - ignoredDelta(oldOutcome);
        if (ignoredDelta != 0) {
            ignoredCalls.addAndGet(ignoredDelta);
        }
    }

    private boolean shouldOpen() {
        final int total = bufferedCalls.get();
        if (total < config.minimumRequiredCalls()) {
            return false;
        }

        return failedCalls.get() * 100 / total >= config.failureRateThreshold();
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
        windowEpoch.incrementAndGet();
        for (int i = 0; i < ringSlots.length(); i++) {
            ringSlots.set(i, 0);
        }
        sequence.set(0);
        bufferedCalls.set(0);
        failedCalls.set(0);
        slowCalls.set(0);
        ignoredCalls.set(0);
        windowEpoch.incrementAndGet();
    }

    private void recordFallback(CallNotPermittedException exception) {
        var observation = this.telemetry.observe();
        try {
            observation.recordCallResult(exception.state(), CallResult.FALLBACK);
        } finally {
            observation.end();
        }
    }

    private static long pack(long epoch, long sequence, int outcome) {
        return (epoch << EPOCH_SHIFT) | ((sequence & SEQUENCE_MASK) << SEQUENCE_SHIFT) | (outcome & OUTCOME_MASK);
    }

    private static long slotEpoch(long epoch) {
        return epoch & 0xFFFFL;
    }

    private static long epoch(long packed) {
        return packed >>> EPOCH_SHIFT;
    }

    private static long sequence(long packed) {
        return (packed >>> SEQUENCE_SHIFT) & SEQUENCE_MASK;
    }

    private static int outcome(long packed) {
        return (int) (packed & OUTCOME_MASK);
    }

    private static int totalDelta(int outcome) {
        return switch (outcome) {
            case OUTCOME_SUCCESS, OUTCOME_FAILURE -> 1;
            default -> 0;
        };
    }

    private static int failureDelta(int outcome) {
        return outcome == OUTCOME_FAILURE ? 1 : 0;
    }

    private static int slowDelta(int outcome) {
        return 0;
    }

    private static int ignoredDelta(int outcome) {
        return outcome == OUTCOME_IGNORED ? 1 : 0;
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    record Snapshot(int total, int failures, int slowCalls, int ignored) {}
}
