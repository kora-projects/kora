package ru.tinkoff.kora.resilient.retry;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.internal.loom.VirtualThreadExecutorHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class KoraRetry implements Retry {

    private static final Logger logger = LoggerFactory.getLogger(KoraRetry.class);

    private static final RetryState EMPTY_STATE = new KoraEmptyRetryState();

    private static class KoraEmptyRetryState implements RetryState {

        @Override
        public @Nonnull RetryStatus onException(@Nonnull Throwable throwable) {
            return RetryStatus.REJECTED;
        }

        @Override
        public int getAttempts() {
            return 0;
        }

        @Override
        public int getAttemptsMax() {
            return 0;
        }

        @Override
        public long getDelayNanos() {
            return 0;
        }

        @Override
        public void doDelay() {

        }

        @Override
        public void close() {

        }
    }

    final String name;
    final long delayNanos;
    final long delayStepNanos;
    final int attempts;
    final RetryPredicate failurePredicate;
    final RetryMetrics metrics;
    final RetryConfig.NamedConfig config;

    KoraRetry(String name,
              long delayNanos,
              long delayStepNanos,
              int attempts,
              RetryPredicate failurePredicate,
              RetryMetrics metrics,
              RetryConfig.NamedConfig config) {
        this.name = name;
        this.delayNanos = delayNanos;
        this.delayStepNanos = delayStepNanos;
        this.attempts = attempts;
        this.failurePredicate = failurePredicate;
        this.metrics = metrics;
        this.config = config;
    }

    KoraRetry(String name, RetryConfig.NamedConfig config, RetryPredicate failurePredicate, RetryMetrics metric) {
        this(name, config.delay().toNanos(), config.delayStep().toNanos(), config.attempts(), failurePredicate, metric, config);
    }

    @Nonnull
    @Override
    public RetryState asState() {
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("Retry '{}' is disabled", name);
            return EMPTY_STATE;
        } else {
            return new KoraRetryState(name, System.nanoTime(), delayNanos, delayStepNanos, attempts, failurePredicate, metrics, new AtomicInteger(0));
        }
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

    @Override
    public <T> CompletionStage<T> retry(@Nonnull Supplier<CompletionStage<T>> supplier) {
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("Retry '{}' is disabled", name);
            return supplier.get();
        }

        if (attempts == 0) {
            return supplier.get();
        }

        var result = new CompletableFuture<T>();
        var retryState = asState();
        var virtualExecutor = VirtualThreadExecutorHolder.executor();
        var retryCallback = new BiConsumer<T, Throwable>() {
            @Override
            public void accept(T r, Throwable e) {
                var ex = (e instanceof CompletionException) ? e.getCause() : e;
                if (ex == null) {
                    result.complete(r);
                    return;
                }

                var state = retryState.onException(ex);
                if (state == Retry.RetryState.RetryStatus.ACCEPTED) {
                    var delayedExecutor = virtualExecutor != null
                        ? CompletableFuture.delayedExecutor(retryState.getDelayNanos(), TimeUnit.NANOSECONDS, virtualExecutor)
                        : CompletableFuture.delayedExecutor(retryState.getDelayNanos(), TimeUnit.NANOSECONDS);

                    delayedExecutor.execute(() -> {
                        try {
                            var resultRetry = supplier.get();
                            resultRetry.whenComplete(this);
                        } catch (Exception se) {
                            CompletableFuture.<T>failedFuture(se).whenComplete(this);
                        }
                    });
                } else if (state == Retry.RetryState.RetryStatus.REJECTED) {
                    retryState.close();
                    result.completeExceptionally(ex);
                } else {
                    retryState.close();
                    result.completeExceptionally(new RetryExhaustedException(name, retryState.getAttemptsMax(), ex));
                }
            }
        };

        try {
            CompletionStage<T> superCall = supplier.get();
            superCall.whenComplete(retryCallback);
            return result;
        } catch (Exception e) {
            CompletableFuture.<T>failedFuture(e).whenComplete(retryCallback);
            return result;
        }
    }

    private <T, E extends Throwable> T internalRetry(RetrySupplier<T, E> consumer, @Nullable RetrySupplier<T, E> fallback) throws E {
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("Retry '{}' is disabled", name);
            return consumer.get();
        }

        if (attempts == 0) {
            return consumer.get();
        }

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

                        final RetryExhaustedException exhaustedException = new RetryExhaustedException(name, attempts, e);
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
