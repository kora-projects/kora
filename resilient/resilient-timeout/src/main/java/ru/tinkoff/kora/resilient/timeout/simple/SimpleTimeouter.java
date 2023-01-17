package ru.tinkoff.kora.resilient.timeout.simple;

import ru.tinkoff.kora.resilient.timeout.TimeoutException;
import ru.tinkoff.kora.resilient.timeout.Timeouter;
import ru.tinkoff.kora.resilient.timeout.telemetry.TimeoutMetrics;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

record SimpleTimeouter(String name, long delayMaxNanos, TimeoutMetrics metrics, ExecutorService executor) implements Timeouter {

    @Nonnull
    @Override
    public Duration timeout() {
        return Duration.ofNanos(delayMaxNanos);
    }

    @Override
    public void execute(@Nonnull Runnable runnable) throws TimeoutException {
        internalExecute(e -> e.submit(runnable));
    }

    @Override
    public <T> T execute(@Nonnull Supplier<T> supplier) throws TimeoutException {
        return internalExecute(e -> e.submit(supplier::get));
    }

    private <T> T internalExecute(Function<ExecutorService, Future<T>> consumer) throws TimeoutException {
        try {
            return consumer.apply(executor).get(delayMaxNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            metrics.recordTimeout(name, delayMaxNanos);
            throw new TimeoutException(e.getMessage());
        }
    }
}
