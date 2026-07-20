package io.koraframework.resilient.circuitbreaker;

import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation.CallAcquireStatus;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation.CallResult;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetry;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * CircuitBreaker - time-based LeapArray implementation.
 * <p>
 * CLOSED-state statistics are stored in a fixed ring of time buckets. The window is time-based, not
 * count-based: it covers the latest configured duration and does not preserve the exact latest N calls.
 * Snapshot is built by summing currently valid buckets and can be briefly eventually consistent around
 * bucket rollover/reset. The state machine and HALF_OPEN counters remain strict and atomic.
 * <p>
 * This implementation is designed for high-RPS hot paths: fixed memory, no per-call allocation, one clock
 * read per CLOSED outcome, and CAS only when a bucket rolls over. {@link CircuitBreakerConfig.TimeBasedCounterType#LONG_ADDER}
 * can reduce contention further, but makes rollover boundaries more approximate than the default striped
 * atomic counters.
 */
@SuppressWarnings("ConstantConditions")
final class TimeBasedKoraCircuitBreaker implements CircuitBreaker {

    private static final long HALF_OPEN_COUNTER_MASK = 0xFFFFL;
    private static final long CLOSED_STATE = 1L << 63;
    private static final long HALF_OPEN_STATE = 1L << 62;
    private static final long HALF_OPEN_INCREMENT_SUCCESS = 1L << 16;

    private static final int OUTCOME_SUCCESS = 1;
    private static final int OUTCOME_FAILURE = 2;
    private static final int OUTCOME_IGNORED = 3;

    private static final long BUCKET_UNINITIALIZED = Long.MIN_VALUE;
    private static final long BUCKET_RESETTING = Long.MIN_VALUE + 1;

    private final AtomicLong state = new AtomicLong(CLOSED_STATE);
    private final String name;
    private final CircuitBreakerConfig.NamedConfig config;
    private final CircuitBreakerPredicate failurePredicate;
    private final CircuitBreakerTelemetry telemetry;
    private final long waitDurationInOpenStateInNanos;
    private final LongSupplier currentTimeNanos;
    private final long startedNanos;
    private final long windowDurationNanos;
    private final long bucketLengthNanos;
    private final Bucket[] buckets;
    private final int bucketMask;
    private final boolean bucketCountPowerOfTwo;
    private final int counterStripeMask;

    TimeBasedKoraCircuitBreaker(String name, CircuitBreakerConfig.NamedConfig config, CircuitBreakerPredicate failurePredicate, CircuitBreakerTelemetry telemetry) {
        this(name, config, failurePredicate, telemetry, System::nanoTime);
    }

    TimeBasedKoraCircuitBreaker(String name, CircuitBreakerConfig.NamedConfig config, CircuitBreakerPredicate failurePredicate, CircuitBreakerTelemetry telemetry, LongSupplier currentTimeNanos) {
        this.name = name;
        this.config = config;
        this.failurePredicate = failurePredicate;
        this.telemetry = telemetry;
        this.waitDurationInOpenStateInNanos = toNanosSaturated(config.waitDurationInOpenState());
        this.currentTimeNanos = currentTimeNanos;
        this.startedNanos = currentTimeNanos.getAsLong();
        this.windowDurationNanos = toNanosSaturated(config.timeBased().windowDuration());
        this.bucketLengthNanos = ceilDiv(this.windowDurationNanos, config.timeBased().sampleCount());
        var bucketCount = config.timeBased().sampleCount();
        this.buckets = new Bucket[bucketCount];
        this.bucketMask = bucketCount - 1;
        this.bucketCountPowerOfTwo = Integer.bitCount(bucketCount) == 1;
        var counterStripes = nextPowerOfTwo(config.timeBased().counterStripes());
        this.counterStripeMask = counterStripes - 1;
        var counterType = config.timeBased().counterType() == null
            ? CircuitBreakerConfig.TimeBasedCounterType.ATOMIC
            : config.timeBased().counterType();
        for (int i = 0; i < bucketCount; i++) {
            this.buckets[i] = new Bucket(counterType, counterStripes);
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
        return snapshot(currentElapsedNanos());
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
                final long now = currentElapsedNanos();
                record(OUTCOME_SUCCESS, now);
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
                    record(OUTCOME_IGNORED, currentElapsedNanos());
                }
                observation.recordCallResult(getState(state.get()), CallResult.IGNORED_FAILURE);
                return;
            }

            final long currentStateLong = state.get();
            final State currentState = getState(currentStateLong);
            if (currentState == State.CLOSED) {
                final long now = currentElapsedNanos();
                record(OUTCOME_FAILURE, now);
                if (shouldOpen(now)) {
                    openFromClosed(observation, throwable, now);
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
            return currentElapsedNanos();
        } else {
            return currentState;
        }
    }

    private void record(int outcome, long now) {
        final Bucket bucket = currentBucket(now);
        bucket.counters.increment(outcome, stripeIndex());
    }

    private Bucket currentBucket(long now) {
        final long bucketStart = bucketStart(now);
        final Bucket bucket = this.buckets[bucketIndex(bucketStart)];
        while (true) {
            final long currentStart = bucket.startNanos.get();
            if (currentStart == bucketStart) {
                return bucket;
            }
            if (currentStart == BUCKET_RESETTING) {
                Thread.onSpinWait();
                continue;
            }
            if (currentStart > bucketStart) {
                return bucket;
            }
            if (bucket.startNanos.compareAndSet(currentStart, BUCKET_RESETTING)) {
                bucket.counters.reset();
                bucket.startNanos.set(bucketStart);
                return bucket;
            }
        }
    }

    private boolean shouldOpen(long now) {
        final Snapshot snapshot = snapshot(now);
        if (snapshot.total() < config.minimumRequiredCalls()) {
            return false;
        }

        return snapshot.failures() * 100 / snapshot.total() >= config.failureRateThreshold();
    }

    private Snapshot snapshot(long now) {
        long total = 0;
        long failures = 0;
        long slowCalls = 0;
        long ignored = 0;
        final long minBucketStart = bucketStart(Math.max(0, now - windowDurationNanos));
        for (Bucket bucket : this.buckets) {
            final long bucketStart = bucket.startNanos.get();
            if (bucketStart >= minBucketStart && bucketStart <= now) {
                final Snapshot snapshot = bucket.counters.snapshot();
                total += snapshot.total();
                failures += snapshot.failures();
                slowCalls += snapshot.slowCalls();
                ignored += snapshot.ignored();
            }
        }
        return new Snapshot(total, failures, slowCalls, ignored);
    }

    private void openFromClosed(CircuitBreakerObservation observation, Throwable throwable, long now) {
        final long currentStateLong = state.get();
        if (getState(currentStateLong) == State.CLOSED && state.compareAndSet(currentStateLong, now)) {
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
        for (Bucket bucket : this.buckets) {
            bucket.startNanos.set(BUCKET_RESETTING);
            bucket.counters.reset();
            bucket.startNanos.set(BUCKET_UNINITIALIZED);
        }
    }

    private long bucketStart(long now) {
        return now - (now % bucketLengthNanos);
    }

    private int bucketIndex(long bucketStart) {
        var bucketPosition = bucketStart / bucketLengthNanos;
        return this.bucketCountPowerOfTwo
            ? (int) (bucketPosition & bucketMask)
            : (int) (bucketPosition % buckets.length);
    }

    private int stripeIndex() {
        var x = (int) Thread.currentThread().threadId();
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        return x & counterStripeMask;
    }

    private void recordFallback(CallNotPermittedException exception) {
        var observation = this.telemetry.observe();
        try {
            observation.recordCallResult(exception.state(), CallResult.FALLBACK);
        } finally {
            observation.end();
        }
    }

    private static int nextPowerOfTwo(int value) {
        var highest = Integer.highestOneBit(value);
        return highest == value ? value : highest << 1;
    }

    private static long ceilDiv(long value, long divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    record Snapshot(long total, long failures, long slowCalls, long ignored) {}

    private interface BucketCounters {

        void increment(int outcome, int stripe);

        Snapshot snapshot();

        void reset();
    }

    private static final class Bucket {

        private final AtomicLong startNanos = new AtomicLong(BUCKET_UNINITIALIZED);
        private final BucketCounters counters;

        private Bucket(CircuitBreakerConfig.TimeBasedCounterType counterType, int counterStripes) {
            this.counters = switch (counterType) {
                case ATOMIC -> new AtomicCounters(counterStripes);
                case LONG_ADDER -> new LongAdderCounters();
            };
        }
    }

    private static final class AtomicCounters implements BucketCounters {

        private final AtomicLongArray total;
        private final AtomicLongArray failures;
        private final AtomicLongArray slowCalls;
        private final AtomicLongArray ignored;

        private AtomicCounters(int stripes) {
            this.total = new AtomicLongArray(stripes);
            this.failures = new AtomicLongArray(stripes);
            this.slowCalls = new AtomicLongArray(stripes);
            this.ignored = new AtomicLongArray(stripes);
        }

        @Override
        public void increment(int outcome, int stripe) {
            switch (outcome) {
                case OUTCOME_SUCCESS -> total.incrementAndGet(stripe);
                case OUTCOME_FAILURE -> {
                    total.incrementAndGet(stripe);
                    failures.incrementAndGet(stripe);
                }
                case OUTCOME_IGNORED -> ignored.incrementAndGet(stripe);
                default -> {
                }
            }
        }

        @Override
        public Snapshot snapshot() {
            long total = 0;
            long failures = 0;
            long slowCalls = 0;
            long ignored = 0;
            for (int i = 0; i < this.total.length(); i++) {
                total += this.total.get(i);
                failures += this.failures.get(i);
                slowCalls += this.slowCalls.get(i);
                ignored += this.ignored.get(i);
            }
            return new Snapshot(total, failures, slowCalls, ignored);
        }

        @Override
        public void reset() {
            for (int i = 0; i < this.total.length(); i++) {
                total.set(i, 0);
                failures.set(i, 0);
                slowCalls.set(i, 0);
                ignored.set(i, 0);
            }
        }
    }

    private static final class LongAdderCounters implements BucketCounters {

        private final LongAdder total = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder slowCalls = new LongAdder();
        private final LongAdder ignored = new LongAdder();

        @Override
        public void increment(int outcome, int stripe) {
            switch (outcome) {
                case OUTCOME_SUCCESS -> total.increment();
                case OUTCOME_FAILURE -> {
                    total.increment();
                    failures.increment();
                }
                case OUTCOME_IGNORED -> ignored.increment();
                default -> {
                }
            }
        }

        @Override
        public Snapshot snapshot() {
            return new Snapshot(total.sum(), failures.sum(), slowCalls.sum(), ignored.sum());
        }

        @Override
        public void reset() {
            total.reset();
            failures.reset();
            slowCalls.reset();
            ignored.reset();
        }
    }
}
