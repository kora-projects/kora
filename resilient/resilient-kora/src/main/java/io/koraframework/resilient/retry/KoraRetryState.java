package io.koraframework.resilient.retry;

import io.koraframework.resilient.retry.telemetry.RetryObservation;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

record KoraRetryState(
    String name,
    long started,
    long delayNanos,
    long delayStepNanos,
    int attemptsMax,
    RetryPredicate failurePredicate,
    RetryObservation observation,
    AtomicInteger attempts
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
        return delayNanos + delayStepNanos * (attempts.get() - 1);
    }

    @Override
    public RetryStatus onException(Throwable throwable) {
        observation.observeError(throwable);
        if (!failurePredicate.test(throwable)) {
            return RetryStatus.REJECTED;
        }

        var attemptsUsed = attempts.incrementAndGet();
        if (attemptsUsed <= attemptsMax) {
            return RetryStatus.ACCEPTED;
        } else {
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
                observation.recordExhaustedAttempts(attemptsMax);
            } else if (attemptsUsed > 0) {
                for (int i = 1; i < attemptsUsed; i++) {
                    final long attemptDelay = delayNanos + delayStepNanos * i;
                    observation.recordAttempt(attemptDelay);
                }
            }
        } finally {
            observation.end();
        }
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
