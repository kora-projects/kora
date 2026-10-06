package io.koraframework.resilient.timeout;

import io.koraframework.common.Principal;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.logging.common.MDC;
import io.koraframework.resilient.common.ThrowableCallable;
import io.koraframework.resilient.common.ThrowableRunnable;
import io.koraframework.resilient.timeout.exception.TimeoutExhaustedException;
import io.koraframework.resilient.timeout.telemetry.TimeoutTelemetry;
import org.jspecify.annotations.Nullable;

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
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public <E extends Throwable> void execute(ThrowableRunnable<E> runnable) throws E, TimeoutExhaustedException {
        execute(() -> {
            runnable.run();
            return null;
        });
    }

    @Override
    public <T, E extends Throwable> T execute(ThrowableCallable<T, E> callable) throws E, TimeoutExhaustedException {
        if (!config.enabled()) {
            return callable.call();
        }

        var observation = telemetry.observe(duration);
        // The call runs on its own thread so the caller is released at the deadline even when the call ignores interrupts.
        // ScopedValue bindings are not inherited by a new thread, so the caller's context is bound there again
        var context = captureContext();
        var future = executor.submit(() -> {
            try {
                return context == null ? callable.call() : context.call(callable::call);
            } catch (Throwable e) {
                throw KoraTimeouter.<Exception>sneakyThrow(e);
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
            throw KoraTimeouter.<RuntimeException>sneakyThrow(cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            observation.observeError(e);
            throw KoraTimeouter.<RuntimeException>sneakyThrow(e);
        } finally {
            observation.end();
        }
    }

    /**
     * Context that the call sees on the caller thread: tracing, MDC and principal.
     * MDC is forked: the call sees the caller's entries, but its own writes stay on the call thread,
     * so an abandoned call cannot race the caller's MDC after the timeout.
     * Other bindings (a JDBC transaction connection, for example) are not carried on purpose:
     * the call is abandoned on timeout and must not keep using the caller's resources after the caller has moved on.
     */
    private static ScopedValue.@Nullable Carrier captureContext() {
        ScopedValue.Carrier carrier = null;
        carrier = bind(carrier, OpentelemetryContext.VALUE);
        carrier = bind(carrier, Observation.VALUE);
        if (MDC.VALUE.isBound()) {
            var mdc = MDC.get().fork();
            carrier = carrier == null ? ScopedValue.where(MDC.VALUE, mdc) : carrier.where(MDC.VALUE, mdc);
        }
        carrier = bind(carrier, Principal.VALUE);
        return carrier;
    }

    private static <T> ScopedValue.@Nullable Carrier bind(ScopedValue.@Nullable Carrier carrier, ScopedValue<T> key) {
        if (!key.isBound()) {
            return carrier;
        }
        var value = key.get();
        return carrier == null ? ScopedValue.where(key, value) : carrier.where(key, value);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }
}
