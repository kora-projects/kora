package ru.tinkoff.kora.resilient.retry.simple;

import ru.tinkoff.kora.resilient.retry.Retrier;
import ru.tinkoff.kora.resilient.retry.RetrierFailurePredicate;
import ru.tinkoff.kora.resilient.retry.RetryAttemptException;
import ru.tinkoff.kora.resilient.retry.RetryException;
import ru.tinkoff.kora.resilient.retry.telemetry.RetryMetrics;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

record SimpleRetrierRetryState(
    String name,
    long started,
    long delayNanos,
    long delayStepNanos,
    int attemptsMax,
    RetrierFailurePredicate failurePredicate,
    RetryMetrics metrics,
    AtomicInteger attempts
) implements Retrier.RetryState {

    @Override
    public long getDelayNanos() {
        return delayNanos + delayStepNanos * attempts.get();
    }

    @Override
    public boolean canRetry(@Nonnull Throwable throwable) {
        if (!failurePredicate.test(throwable)) {
            return false;
        }

        return attempts.incrementAndGet() <= attemptsMax;
    }

    @Override
    public void checkRetry(@Nonnull Throwable throwable) throws RetryException {
        if (!failurePredicate.test(throwable)) {
            throw (throwable instanceof RuntimeException re)
                ? re
                : new RetryException(throwable);
        }

        if (attempts.incrementAndGet() > attemptsMax) {
            metrics.recordExhaustedAttempts(name, attemptsMax);
            throw new RetryAttemptException("All '" + attemptsMax + "' attempts elapsed during retry");
        }
    }

    @Override
    public void doDelay() {
        long nextDelayNanos = getDelayNanos();
        metrics.recordAttempt(name, nextDelayNanos);
        sleepUninterruptibly(nextDelayNanos);
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
