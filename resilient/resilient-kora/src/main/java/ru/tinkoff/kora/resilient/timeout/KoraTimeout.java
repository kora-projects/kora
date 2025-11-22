package ru.tinkoff.kora.resilient.timeout;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Function;

final class KoraTimeout implements Timeout {

    private static final Logger logger = LoggerFactory.getLogger(KoraTimeout.class);

    private final String name;
    private final long delayMaxNanos;
    private final TimeoutMetrics metrics;
    private final TimeoutConfig.NamedConfig config;
    private final Executor executor;

    KoraTimeout(String name,
                long delayMaxNanos,
                TimeoutMetrics metrics,
                TimeoutConfig.NamedConfig config) {
        this.name = name;
        this.delayMaxNanos = delayMaxNanos;
        this.metrics = metrics;
        this.config = config;
        var threadFactory = Thread.ofVirtual()
            .name("timeout-" + name + "-", 1)
            .factory();
        this.executor = e -> threadFactory.newThread(e).start();
    }

    @Nonnull
    @Override
    public Duration timeout() {
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("Timeout '{}' is disabled", name);
            return Duration.ZERO;
        } else {
            return Duration.ofNanos(delayMaxNanos);
        }
    }

    @Override
    public void execute(@Nonnull Runnable runnable) throws TimeoutExhaustedException {
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("Timeout '{}' is disabled", name);
            runnable.run();
        }

        internalExecute(e -> {
            var future = new CompletableFuture<Void>();
            e.execute(() -> {
                try {
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
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("Timeout '{}' is disabled", name);

            try {
                return callable.call();
            } catch (Exception e) {
                KoraTimeouterUtils.doThrow(e.getCause());
            }

            // is not executed
            throw new IllegalStateException("Should not happen");
        }

        return internalExecute(e -> {
            var future = new CompletableFuture<T>();
            e.execute(() -> {
                try {
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
                KoraTimeouterUtils.doThrow(e.getCause());
            }
        } catch (TimeoutException e) {
            handler.cancel(true);
            final Duration timeout = timeout();
            logger.debug("KoraTimeout '{}' registered timeout after: {}", name, timeout);
            metrics.recordTimeout(name, delayMaxNanos);
            throw new TimeoutExhaustedException(name, "Timeout exceeded " + timeout);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        // is not executed
        throw new IllegalStateException("Should not happen");
    }

    public String name() {return name;}

    public long delayMaxNanos() {return delayMaxNanos;}

    public TimeoutMetrics metrics() {return metrics;}

    public TimeoutConfig.NamedConfig config() {return config;}

    public Executor executor() {return executor;}

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (KoraTimeout) obj;
        return Objects.equals(this.name, that.name) &&
            this.delayMaxNanos == that.delayMaxNanos &&
            Objects.equals(this.metrics, that.metrics) &&
            Objects.equals(this.config, that.config) &&
            Objects.equals(this.executor, that.executor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, delayMaxNanos, metrics, config, executor);
    }

    @Override
    public String toString() {
        return "KoraTimeout[" +
            "name=" + name + ", " +
            "delayMaxNanos=" + delayMaxNanos + ", " +
            "metrics=" + metrics + ", " +
            "config=" + config + ", " +
            "executor=" + executor + ']';
    }

}
