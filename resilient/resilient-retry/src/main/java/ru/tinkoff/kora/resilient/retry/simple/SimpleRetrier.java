package ru.tinkoff.kora.resilient.retry.simple;

import reactor.util.retry.Retry;
import ru.tinkoff.kora.resilient.retry.Retrier;
import ru.tinkoff.kora.resilient.retry.RetrierFailurePredicate;
import ru.tinkoff.kora.resilient.retry.RetryAttemptException;
import ru.tinkoff.kora.resilient.retry.telemetry.RetryMetrics;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class SimpleRetrier implements Retrier {
    private final String name;
    private final long delayNanos;
    private final long delayStepNanos;
    private final int attempts;
    private final RetrierFailurePredicate failurePredicate;
    private final RetryMetrics metrics;

    SimpleRetrier(String name,
                  long delayNanos,
                  long delayStepNanos,
                  int attempts,
                  RetrierFailurePredicate failurePredicate,
                  RetryMetrics metrics) {
        this.name = name;
        this.delayNanos = delayNanos;
        this.delayStepNanos = delayStepNanos;
        this.attempts = attempts;
        this.failurePredicate = failurePredicate;
        this.metrics = metrics;
    }

    SimpleRetrier(String name, SimpleRetrierConfig.NamedConfig config, RetrierFailurePredicate failurePredicate, RetryMetrics metric) {
        this(name, config.delay().toNanos(), config.delayStep().toNanos(), config.attempts(), failurePredicate, metric);
    }

    @Nonnull
    @Override
    public RetryState asState() {
        return new SimpleRetryState(name, System.nanoTime(), delayNanos, delayStepNanos, attempts, failurePredicate, metrics, new AtomicInteger(0));
    }

    @Nonnull
    @Override
    public Retry asReactor() {
        return new SimpleReactorRetry(name, delayNanos, delayStepNanos, attempts, failurePredicate, metrics);
    }

    @Override
    public void retry(@Nonnull Runnable runnable) {
        internalRetry(() -> {
            runnable.run();
            return null;
        }, null);
    }

    @Override
    public <T> T retry(@Nonnull Supplier<T> supplier) {
        return internalRetry(supplier, null);
    }

    @Override
    public <T> T retry(@Nonnull Supplier<T> supplier, @Nonnull Supplier<T> fallback) {
        return internalRetry(supplier, fallback);
    }

    private <T> T internalRetry(Supplier<T> consumer, @Nullable Supplier<T> fallback) {
        var cause = (Exception) null;
        try (var state = asState()) {
            while (true) {
                try {
                    return consumer.get();
                } catch (Exception e) {
                    var status = state.onException(e);
                    if (status == RetryState.RetryStatus.REJECTED) {
                        throw e;
                    } else if (status == RetryState.RetryStatus.ACCEPTED) {
                        if (cause == null) {
                            cause = e;
                        } else {
                            cause.addSuppressed(e);
                        }
                        state.doDelay();
                    } else if (status == RetryState.RetryStatus.EXHAUSTED) {
                        if (fallback != null) {
                            try {
                                return fallback.get();
                            } catch (Exception ex) {
                                if (cause != null) {
                                    ex.addSuppressed(cause);
                                }
                                throw ex;
                            }
                        }

                        var exhaustedException = new RetryAttemptException(state.getAttempts());
                        if (cause != null) {
                            exhaustedException.addSuppressed(cause);
                        }
                        throw exhaustedException;
                    }
                }
            }
        }
    }
}
