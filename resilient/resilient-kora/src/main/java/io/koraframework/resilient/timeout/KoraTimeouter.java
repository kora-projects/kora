package io.koraframework.resilient.timeout;

import io.koraframework.resilient.timeout.exception.TimeoutExhaustedException;
import io.koraframework.resilient.timeout.telemetry.TimeoutTelemetry;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;

public class KoraTimeouter implements Timeouter {

    private final String name;
    private final Duration duration;
    private final TimeoutTelemetry telemetry;
    private final TimeoutConfig config;
    private final ExecutorService executor;

    public KoraTimeouter(String name, Duration duration, TimeoutTelemetry telemetry, TimeoutConfig config) {
        this.name = name;
        this.duration = Objects.requireNonNull(duration);
        this.telemetry = telemetry;
        this.config = config;
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("timeout-" + name + "-", 1).factory());
    }

    @Override
    public Duration timeout() {
        return duration;
    }

    @Override
    public <E extends Throwable> void execute(TimeoutRunnable<E> runnable) throws E, TimeoutExhaustedException {
        execute(() -> {
            runnable.run();
            return null;
        });
    }

    @Override
    public <T, E extends Throwable> T execute(TimeoutCallable<T, E> callable) throws E, TimeoutExhaustedException {
        if (!config.enabled()) {
            return callable.call();
        }

        var observation = telemetry.observe(duration);
        var future = executor.submit(() -> {
            try {
                return callable.call();
            } catch (Throwable e) {
                KoraTimeouterUtils.doThrow(e);
                return null;
            }
        });
        try {
            return future.get(duration.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            observation.recordTimeout(duration.toNanos());
            throw new TimeoutExhaustedException(name, "Timeout exceeded " + duration);
        } catch (ExecutionException e) {
            var cause = e.getCause();
            observation.observeError(cause);
            KoraTimeouterUtils.doThrow(cause);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            observation.observeError(e);
            KoraTimeouterUtils.doThrow(e);
            return null;
        } finally {
            observation.end();
        }
    }
}
