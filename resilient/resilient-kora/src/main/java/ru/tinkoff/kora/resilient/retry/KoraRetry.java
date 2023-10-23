package ru.tinkoff.kora.resilient.retry;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class KoraRetry implements Retry {

    final String name;
    final long delayNanos;
    final long delayStepNanos;
    final int attempts;
    final RetryPredicate failurePredicate;
    final RetryMetrics metrics;

    KoraRetry(String name,
              long delayNanos,
              long delayStepNanos,
              int attempts,
              RetryPredicate failurePredicate,
              RetryMetrics metrics) {
        this.name = name;
        this.delayNanos = delayNanos;
        this.delayStepNanos = delayStepNanos;
        this.attempts = attempts;
        this.failurePredicate = failurePredicate;
        this.metrics = metrics;
    }

    KoraRetry(String name, RetryConfig.NamedConfig config, RetryPredicate failurePredicate, RetryMetrics metric) {
        this(name, config.delay().toNanos(), config.delayStep().toNanos(), config.attempts(), failurePredicate, metric);
    }

    @Nonnull
    @Override
    public RetryState asState() {
        return new KoraRetryState(name, System.nanoTime(), delayNanos, delayStepNanos, attempts, failurePredicate, metrics, new AtomicInteger(0));
    }

    @Override
    public <E extends Throwable> void retry(@Nonnull RetryRunnable<E> runnable) throws RetryExhaustedException, E {
        internalRetry(() -> {
            runnable.run();
            return null;
        }, null);
    }

    @Override
    public <T, E extends Throwable> T retry(@Nonnull RetrySupplier<T, E> supplier) throws E {
        return internalRetry(supplier, null);
    }

    @Override
    public <T, E extends Throwable> T retry(@Nonnull RetrySupplier<T, E> supplier, @Nonnull RetrySupplier<T, E> fallback) throws E {
        return internalRetry(supplier, fallback);
    }

    private <T, E extends Throwable> T internalRetry(RetrySupplier<T, E> consumer, @Nullable RetrySupplier<T, E> fallback) throws E {
        final List<Exception> suppressed = new ArrayList<>();
        try (var state = asState()) {
            while (true) {
                try {
                    return consumer.get();
                } catch (Exception e) {
                    var status = state.onException(e);
                    if (status == RetryState.RetryStatus.REJECTED) {
                        for (Exception exception : suppressed) {
                            e.addSuppressed(exception);
                        }

                        throw e;
                    } else if (status == RetryState.RetryStatus.ACCEPTED) {
                        suppressed.add(e);
                        state.doDelay();
                    } else if (status == RetryState.RetryStatus.EXHAUSTED) {
                        if (fallback != null) {
                            try {
                                return fallback.get();
                            } catch (Exception ex) {
                                for (Exception exception : suppressed) {
                                    ex.addSuppressed(exception);
                                }
                                throw ex;
                            }
                        }

                        final RetryExhaustedException exhaustedException = new RetryExhaustedException(attempts, e);
                        for (Exception exception : suppressed) {
                            exhaustedException.addSuppressed(exception);
                        }

                        throw exhaustedException;
                    }
                }
            }
        }
    }
}
