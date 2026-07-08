package io.koraframework.resilient.retry;

import io.koraframework.resilient.retry.exception.RetryExhaustedException;
import io.koraframework.resilient.retry.telemetry.RetryObservation;
import io.koraframework.resilient.retry.telemetry.RetryObservation.StopReason;
import io.koraframework.resilient.retry.telemetry.RetryTelemetry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class KoraRetry implements Retry {

    public static final Logger logger = LoggerFactory.getLogger(KoraRetry.class);

    private static final RetryState EMPTY_STATE = new KoraEmptyRetryState();
    private final Executor executor;

    private static class KoraEmptyRetryState implements RetryState {

        @Override
        public RetryStatus onException(Throwable throwable) {
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
        public void doDelay() {}

        @Override
        public void close() {}
    }

    final String name;
    final long delayNanos;
    final long delayStepNanos;
    final int attempts;
    final RetryPredicate failurePredicate;
    @Nullable
    final KoraRetryBudget retryBudget;
    final RetryTelemetry telemetry;
    final RetryConfig.NamedConfig config;

    KoraRetry(String name,
              long delayNanos,
              long delayStepNanos,
              int attempts,
              RetryPredicate failurePredicate,
              @Nullable KoraRetryBudget retryBudget,
              RetryTelemetry telemetry,
              RetryConfig.NamedConfig config) {
        this.name = name;
        this.delayNanos = delayNanos;
        this.delayStepNanos = delayStepNanos;
        this.attempts = attempts;
        this.failurePredicate = failurePredicate;
        this.retryBudget = retryBudget;
        this.telemetry = telemetry;
        this.config = config;
        var threadFactory = Thread.ofVirtual().name("retry-" + name + "-", 1).factory();
        this.executor = r -> threadFactory.newThread(r).start();
    }

    KoraRetry(String name,
              RetryConfig.NamedConfig config,
              RetryPredicate failurePredicate,
              @Nullable KoraRetryBudget retryBudget,
              RetryTelemetry telemetry) {
        this(name, config.delay().toNanos(), config.delayStep().toNanos(), config.attempts(), failurePredicate, retryBudget, telemetry, config);
    }

    @Override
    public RetryState asState() {
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("Retry '{}' is disabled", name);
            return EMPTY_STATE;
        } else {
            return new KoraRetryState(
                name,
                System.nanoTime(),
                delayNanos,
                delayStepNanos,
                attempts,
                failurePredicate,
                telemetry.observe(),
                new AtomicInteger(0),
                new AtomicBoolean(false),
                new AtomicBoolean(false),
                config.backoff(),
                config.jitter(),
                retryBudget
            );
        }
    }

    @Override
    public <E extends Throwable> void retry(RetryRunnable<E> runnable) throws RetryExhaustedException, E {
        RetrySupplier<Void, E> supplier = () -> {
            runnable.run();
            return null;
        };
        retry(supplier);
    }

    @Override
    public <T, E extends Throwable> T retry(RetrySupplier<T, E> supplier) throws E {
        return retry(supplier, null);
    }

    @Override
    public <T, E extends Throwable> T retry(RetrySupplier<T, E> supplier, @Nullable RetrySupplier<T, E> fallback) throws E {
        if (hasNewOptions()) {
            return enhancedRetry(supplier, fallback);
        }
        return legacyRetry(supplier, fallback);
    }

    @Override
    public <T> CompletionStage<T> retry(Supplier<CompletionStage<T>> supplier) {
        if (hasNewOptions()) {
            return enhancedRetryAsync(supplier);
        }
        return legacyRetryAsync(supplier);
    }

    private boolean hasNewOptions() {
        return config.backoff() != null || isJitterEnabled() || retryBudget != null;
    }

    private <T> CompletionStage<T> legacyRetryAsync(Supplier<CompletionStage<T>> supplier) {
        if (Boolean.FALSE.equals(config.enabled())) {
            return supplier.get();
        }

        if (attempts == 0) {
            return supplier.get();
        }

        var result = new CompletableFuture<T>();
        var retryState = asState();
        var retryCallback = new BiConsumer<T, Throwable>() {
            @Override
            public void accept(T r, Throwable e) {
                var ex = (e instanceof CompletionException) ? e.getCause() : e;
                if (ex == null) {
                    retryState.close();
                    result.complete(r);
                    return;
                }

                var state = retryState.onException(ex);
                if (state == Retry.RetryState.RetryStatus.ACCEPTED) {
                    var delayedExecutor = CompletableFuture.delayedExecutor(retryState.getDelayNanos(), TimeUnit.NANOSECONDS, executor);

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

    private <T, E extends Throwable> T legacyRetry(RetrySupplier<T, E> consumer, @Nullable RetrySupplier<T, E> fallback) throws E {
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

    private <T, E extends Throwable> T enhancedRetry(RetrySupplier<T, E> supplier, @Nullable RetrySupplier<T, E> fallback) throws E {
        if (Boolean.FALSE.equals(config.enabled()) || attempts == 0) {
            return supplier.get();
        }

        var observation = telemetry.observe();
        var suppressed = new ArrayList<Exception>();
        int retryAttempt = 1;
        try {
            while (true) {
                try {
                    var result = supplier.get();
                    onSuccess();
                    return result;
                } catch (Exception e) {
                    observation.observeError(e);
                    if (!failurePredicate.test(e)) {
                        addSuppressed(e, suppressed);
                        throw e;
                    }
                    if (retryAttempt > attempts) {
                        observation.recordExhausted(StopReason.EXHAUSTED_ATTEMPTS, attempts);
                        if (fallback != null) {
                            try {
                                return fallback.get();
                            } catch (Exception ex) {
                                addSuppressed(ex, suppressed);
                                throw ex;
                            }
                        }
                        var exhausted = new RetryExhaustedException(name, attempts, e);
                        for (Exception exception : suppressed) {
                            exhausted.addSuppressed(exception);
                        }
                        throw exhausted;
                    }
                    if (retryBudget != null && !retryBudget.tryAcquireRetryToken()) {
                        observation.recordExhausted(StopReason.EXHAUSTED_BUDGET, retryAttempt - 1);
                        addSuppressed(e, suppressed);
                        throw e;
                    }

                    var delayNanos = delayNanos(retryAttempt);
                    observation.recordAttempt(delayNanos);
                    suppressed.add(e);
                    sleepUninterruptibly(delayNanos);
                    retryAttempt++;
                }
            }
        } finally {
            observation.end();
        }
    }

    private <T> CompletionStage<T> enhancedRetryAsync(Supplier<CompletionStage<T>> supplier) {
        if (Boolean.FALSE.equals(config.enabled()) || attempts == 0) {
            return supplier.get();
        }

        var result = new CompletableFuture<T>();
        var observation = telemetry.observe();
        executeEnhancedAttempt(supplier, result, observation, 1);
        return result;
    }

    private <T> void executeEnhancedAttempt(Supplier<CompletionStage<T>> supplier, CompletableFuture<T> result, RetryObservation observation, int retryAttempt) {
        try {
            supplier.get().whenComplete((r, e) -> handleEnhancedAsyncResult(supplier, result, observation, retryAttempt, r, e));
        } catch (Exception e) {
            CompletableFuture.<T>failedFuture(e).whenComplete((r, failure) -> handleEnhancedAsyncResult(supplier, result, observation, retryAttempt, r, failure));
        }
    }

    private <T> void handleEnhancedAsyncResult(Supplier<CompletionStage<T>> supplier, CompletableFuture<T> result, RetryObservation observation, int retryAttempt, T r, Throwable e) {
        var ex = unwrap(e);
        if (ex == null) {
            onSuccess();
            observation.end();
            result.complete(r);
            return;
        }

        observation.observeError(ex);
        if (!failurePredicate.test(ex)) {
            observation.end();
            result.completeExceptionally(ex);
            return;
        }
        if (retryAttempt > attempts) {
            observation.recordExhausted(StopReason.EXHAUSTED_ATTEMPTS, attempts);
            observation.end();
            result.completeExceptionally(new RetryExhaustedException(name, attempts, ex));
            return;
        }
        if (retryBudget != null && !retryBudget.tryAcquireRetryToken()) {
            observation.recordExhausted(StopReason.EXHAUSTED_BUDGET, retryAttempt - 1);
            observation.end();
            result.completeExceptionally(ex);
            return;
        }

        var delayNanos = delayNanos(retryAttempt);
        observation.recordAttempt(delayNanos);
        CompletableFuture.delayedExecutor(delayNanos, TimeUnit.NANOSECONDS, executor)
            .execute(() -> executeEnhancedAttempt(supplier, result, observation, retryAttempt + 1));
    }

    long computedDelayNanos(int retryAttempt) {
        if (config.backoff() == null) {
            return delayNanos + delayStepNanos * (retryAttempt - 1);
        }

        var multiplier = config.backoff().multiplier();
        var delay = delayNanos * Math.pow(multiplier, retryAttempt - 1);
        var computed = delay >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) delay;
        if (config.backoff().maxDelay() == null) {
            return computed;
        }
        return Math.min(computed, config.backoff().maxDelay().toNanos());
    }

    long delayNanos(int retryAttempt) {
        var computed = computedDelayNanos(retryAttempt);
        if (!isJitterEnabled() || computed <= 0) {
            return computed;
        }
        var jitterBound = (long) (computed * config.jitter().ratio());
        if (jitterBound <= 0) {
            return computed;
        }
        var minimum = computed - jitterBound;
        return minimum + randomNanos(jitterBound);
    }

    private boolean isJitterEnabled() {
        return config.jitter() != null && config.jitter().type() == RetryConfig.JitterType.FULL && config.jitter().ratio() > 0;
    }

    private static long randomNanos(long inclusiveBound) {
        if (inclusiveBound == Long.MAX_VALUE) {
            return ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        }
        return ThreadLocalRandom.current().nextLong(inclusiveBound + 1);
    }

    private void onSuccess() {
        if (retryBudget != null) {
            retryBudget.onSuccess();
        }
    }

    @Nullable
    private static Throwable unwrap(@Nullable Throwable e) {
        return e instanceof CompletionException ? e.getCause() : e;
    }

    private static void addSuppressed(Exception e, List<Exception> suppressed) {
        for (Exception exception : suppressed) {
            if (exception != e) {
                e.addSuppressed(exception);
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
