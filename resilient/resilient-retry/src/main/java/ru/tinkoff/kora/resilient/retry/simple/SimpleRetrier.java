package ru.tinkoff.kora.resilient.retry.simple;

import reactor.util.retry.Retry;
import ru.tinkoff.kora.resilient.retry.Retrier;
import ru.tinkoff.kora.resilient.retry.RetrierFailurePredicate;
import ru.tinkoff.kora.resilient.retry.RetryAttemptException;
import ru.tinkoff.kora.resilient.retry.RetryException;
import ru.tinkoff.kora.resilient.retry.telemetry.RetryMetrics;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

record SimpleRetrier(String name,
                     long delayNanos,
                     long delayStepNanos,
                     int attempts,
                     RetrierFailurePredicate failurePredicate,
                     RetryMetrics metrics,
                     ExecutorService executor) implements Retrier {

    public SimpleRetrier(String name, SimpleRetrierConfig.NamedConfig config, RetrierFailurePredicate failurePredicate, RetryMetrics metrics, ExecutorService executors) {
        this(name, config.delay().toNanos(), config.delayStep().toNanos(), config.attempts(), failurePredicate, metrics, executors);
    }

    @Nonnull
    @Override
    public RetryState asState() {
        return new SimpleRetrierRetryState(name, System.nanoTime(), delayNanos, delayStepNanos, attempts, failurePredicate, metrics, new AtomicInteger(0));
    }

    @Nonnull
    @Override
    public Retry asReactor() {
        return new SimpleReactorRetry(name, delayNanos, delayStepNanos, attempts, failurePredicate, metrics);
    }

    @Override
    public void retry(@Nonnull Runnable runnable) throws RetryException {
        internalRetry(e -> e.submit(runnable), null);
    }

    @Override
    public <T> T retry(@Nonnull Supplier<T> supplier) throws RetryException {
        return internalRetry(e -> e.submit(supplier::get), null);
    }

    @Override
    public <T> T retry(@Nonnull Supplier<T> supplier, @Nonnull Supplier<T> fallback) throws RetryException {
        return internalRetry(e -> e.submit(supplier::get), fallback);
    }

    private <T> T internalRetry(Function<ExecutorService, Future<T>> consumer, @Nullable Supplier<T> fallback) throws RetryException {
        var counter = asState();
        for (int i = 0; i < attempts; i++) {
            try {
                return consumer.apply(executor).get(24, TimeUnit.HOURS);
            } catch (Throwable e) {
                counter.checkRetry(e);
                counter.doDelay();
            }
        }

        if (fallback != null) {
            return fallback.get();
        }

        throw new RetryAttemptException("All '" + attempts + "' attempts elapsed during retry");
    }
}
