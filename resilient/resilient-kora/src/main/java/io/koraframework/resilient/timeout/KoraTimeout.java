package io.koraframework.resilient.timeout;

import io.koraframework.resilient.timeout.telemetry.TimeoutTelemetry;
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
    private final Duration delayMaxDuration;
    private final TimeoutTelemetry telemetry;
    private final TimeoutConfig.NamedConfig config;
    private final Executor executor;

    KoraTimeout(String name,
                Duration delayMaxDuration,
                TimeoutTelemetry telemetry,
                TimeoutConfig.NamedConfig config) {
        this.name = name;
        this.delayMaxNanos = delayMaxDuration.toNanos();
        this.delayMaxDuration = delayMaxDuration;
        this.telemetry = telemetry;
        this.config = config;
        var threadFactory = Thread.ofVirtual()
            .name("timeout-" + name + "-", 1)
            .factory();
        this.executor = e -> threadFactory.newThread(e).start();
    }

    @Override
    public Duration timeout() {
        if (Boolean.FALSE.equals(config.enabled())) {
            return Duration.ZERO;
        } else {
            return delayMaxDuration;
        }
    }

    @Override
    public void execute(Runnable runnable) throws TimeoutExhaustedException {
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("Timeout '{}' is disabled", name);

            runnable.run();
            return;
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
    public <T> T execute(Callable<T> callable) throws TimeoutExhaustedException {
        if (Boolean.FALSE.equals(config.enabled())) {
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
        var observation = this.telemetry.observe(delayMaxDuration);
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
            observation.recordTimeout(delayMaxNanos);
            observation.observeError(e);
            throw new TimeoutExhaustedException(name, "Timeout exceeded " + timeout);
        } catch (InterruptedException e) {
            observation.observeError(e);
            throw new IllegalStateException(e);
        } finally {
            observation.end();
        }

        // is not executed
        throw new IllegalStateException("Should not happen");
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (KoraTimeout) obj;
        return Objects.equals(this.name, that.name) && this.delayMaxNanos == that.delayMaxNanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, delayMaxNanos);
    }

    @Override
    public String toString() {
        return "KoraTimeout[name=" + name + ", " + "delayMaxNanos=" + delayMaxNanos + ']';
    }
}
