package ru.tinkoff.kora.resilient.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

record KoraRetryState(
    String name,
    long started,
    long delayNanos,
    long delayStepNanos,
    int attemptsMax,
    RetryPredicate failurePredicate,
    RetryMetrics metrics,
    AtomicInteger attempts
) implements Retry.RetryState {

    private static final Logger logger = LoggerFactory.getLogger(KoraRetryState.class);

    @Override
    public int getAttempts() {
        final int usedAttempts = attempts.get();
        return (usedAttempts > attemptsMax)
            ? attemptsMax
            : usedAttempts;
    }

    @Override
    public long getDelayNanos() {
        return delayNanos + delayStepNanos * (attempts.get() - 1);
    }

    @Nonnull
    @Override
    public RetryStatus onException(@Nonnull Throwable throwable) {
        if (!failurePredicate.test(throwable)) {
            logger.trace("RetryState '{}' rejected exception: {}", name, throwable.getClass().getCanonicalName());
            return RetryStatus.REJECTED;
        }

        var attemptsUsed = attempts.incrementAndGet();
        if (attemptsUsed <= attemptsMax) {
            if (logger.isTraceEnabled()) {
                logger.trace("RetryState '{}' initiating '{}' retry attempt in '{}' due to exception: {}",
                    name, attemptsUsed, Duration.ofNanos(getDelayNanos()), throwable.getClass().getCanonicalName());
            }

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
        if (attemptsUsed > attemptsMax) {
            logger.debug("RetryState '{}' exhausted all '{}' retry attempts", name, attemptsMax);
            metrics.recordExhaustedAttempts(name, attemptsMax);
        } else if (attemptsUsed > 0) {
            logger.trace("RetryState '{}' success after '{}' failed retry attempts", name, attemptsUsed);
            for (int i = 1; i < attemptsUsed; i++) {
                final long attemptDelay = delayNanos + delayStepNanos * i;
                metrics.recordAttempt(name, attemptDelay);
            }
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
