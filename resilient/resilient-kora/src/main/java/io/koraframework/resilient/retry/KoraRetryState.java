package io.koraframework.resilient.retry;

import io.koraframework.resilient.retry.telemetry.RetryObservation;
import io.koraframework.resilient.retry.telemetry.RetryObservation.StopReason;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

record KoraRetryState(
    String name,
    long started,
    long delayNanos,
    long delayStepNanos,
    int attemptsMax,
    RetryPredicate failurePredicate,
    RetryObservation observation,
    AtomicInteger attempts,
    AtomicBoolean terminalFailure,
    AtomicBoolean budgetDenied,
    RetryConfig.BackoffConfig backoff,
    RetryConfig.JitterConfig jitter,
    @Nullable KoraRetryBudget retryBudget
) implements Retry.RetryState {

    @Override
    public int getAttempts() {
        final int usedAttempts = attempts.get();
        return Math.min(usedAttempts, attemptsMax);
    }

    @Override
    public int getAttemptsMax() {
        return attemptsMax;
    }

    @Override
    public long getDelayNanos() {
        return delayForAttempt(attempts.get());
    }

    @Override
    public RetryStatus onException(Throwable throwable) {
        observation.observeError(throwable);
        if (!failurePredicate.test(throwable)) {
            terminalFailure.set(true);
            return RetryStatus.REJECTED;
        }

        var attemptsUsed = attempts.incrementAndGet();
        if (attemptsUsed <= attemptsMax) {
            if (retryBudget != null && !retryBudget.tryAcquireRetryToken()) {
                terminalFailure.set(true);
                budgetDenied.set(true);
                observation.recordExhausted(StopReason.EXHAUSTED_BUDGET, attemptsUsed - 1);
                return RetryStatus.REJECTED;
            }
            return RetryStatus.ACCEPTED;
        } else {
            terminalFailure.set(true);
            return RetryStatus.EXHAUSTED;
        }
    }

    @Override
    public void doDelay() {
        long nextDelayNanos = getDelayNanos();
        sleepUninterruptibly(nextDelayNanos);
    }

    @Override
    public void close() {
        var attemptsUsed = attempts.get();
        try {
            if (attemptsUsed > attemptsMax) {
                observation.recordExhausted(StopReason.EXHAUSTED_ATTEMPTS, attemptsMax);
            } else if (attemptsUsed > 0) {
                for (int i = 1; i < attemptsUsed; i++) {
                    final long attemptDelay = computedDelayForAttempt(i);
                    observation.recordAttempt(attemptDelay);
                }
            }
            if (retryBudget != null && !terminalFailure.get() && !budgetDenied.get()) {
                retryBudget.onSuccess();
            }
        } finally {
            observation.end();
        }
    }

    private long delayForAttempt(int attempt) {
        var computed = computedDelayForAttempt(attempt);
        if (!isJitterEnabled() || computed <= 0) {
            return computed;
        }
        var jitterBound = (long) (computed * jitter.ratio());
        if (jitterBound <= 0) {
            return computed;
        }
        var minimum = computed - jitterBound;
        return minimum + randomNanos(jitterBound);
    }

    private long computedDelayForAttempt(int attempt) {
        if (backoff == null) {
            return delayNanos + delayStepNanos * (attempt - 1);
        }

        var delay = delayNanos * Math.pow(backoff.multiplier(), attempt - 1);
        var computed = delay >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) delay;
        if (backoff.maxDelay() == null) {
            return computed;
        }
        return Math.min(computed, backoff.maxDelay().toNanos());
    }

    private boolean isJitterEnabled() {
        return jitter != null && jitter.type() == RetryConfig.JitterType.FULL && jitter.ratio() > 0;
    }

    private static long randomNanos(long inclusiveBound) {
        if (inclusiveBound == Long.MAX_VALUE) {
            return ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        }
        return ThreadLocalRandom.current().nextLong(inclusiveBound + 1);
    }

    private static void sleepUninterruptibly(final long sleepForNanos) {
        boolean interrupted = false;

        try {
            long remainingNanos = sleepForNanos;
            long end = System.nanoTime() + remainingNanos;

            while (true) {
                try {
                    TimeUnit.NANOSECONDS.sleep(remainingNanos);
                    return;
                } catch (InterruptedException ex) {
                    interrupted = true;
                    remainingNanos = end - System.nanoTime();
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
