package ru.tinkoff.kora.resilient.timeout;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Context;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Function;

record KoraTimeout(String name, long delayMaxNanos, TimeoutMetrics metrics, Executor executor) implements Timeout {

    private static final Logger logger = LoggerFactory.getLogger(KoraTimeout.class);

    @Nonnull
    @Override
    public Duration timeout() {
        return Duration.ofNanos(delayMaxNanos);
    }

    @Override
    public void execute(@Nonnull Runnable runnable) throws TimeoutExhaustedException {
        internalExecute(e -> {
            var future = new CompletableFuture<Void>();
            Context current = Context.current();
            e.execute(() -> {
                try {
                    current.inject();
                    runnable.run();
                    future.complete(null);
                } catch (Throwable ex) {
                    future.completeExceptionally(ex);
                }
            });
            return future;
        });
    }

    @Override
    public <T> T execute(@Nonnull Callable<T> callable) throws TimeoutExhaustedException {
        return internalExecute(e -> {
            var future = new CompletableFuture<T>();
            Context current = Context.current();
            e.execute(() -> {
                try {
                    current.inject();
                    var result = callable.call();
                    future.complete(result);
                } catch (Throwable ex) {
                    future.completeExceptionally(ex);
                }
            });
            return future;
        });
    }

    private <T> T internalExecute(Function<Executor, Future<T>> consumer) throws TimeoutExhaustedException {
        if (logger.isTraceEnabled()) {
            final Duration timeout = timeout();
            logger.trace("KoraTimeout '{}' starting await for {}", name, timeout);
        }

        final Future<T> handler = consumer.apply(executor);
        try {
            return handler.get(delayMaxNanos, TimeUnit.NANOSECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            } else {
                throw new CompletionException(e.getCause());
            }
        } catch (java.util.concurrent.TimeoutException e) {
            handler.cancel(true);
            final Duration timeout = timeout();
            logger.debug("KoraTimeout '{}' registered timeout after: {}", name, timeout);
            metrics.recordTimeout(name, delayMaxNanos);
            throw new TimeoutExhaustedException(name, "Timeout exceeded " + timeout);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
