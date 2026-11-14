package io.koraframework.resilient.timeout;

import io.koraframework.common.Principal;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.logging.common.MDC;
import io.koraframework.resilient.common.ThrowableCallable;
import io.koraframework.resilient.retry.KoraRetry;
import io.koraframework.resilient.retry.RetryConfig;
import io.koraframework.resilient.retry.telemetry.impl.NoopRetryTelemetry;
import io.koraframework.resilient.timeout.exception.TimeoutExhaustedException;
import io.koraframework.resilient.timeout.telemetry.impl.NoopTimeoutTelemetry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class KoraTimeouterTests {

    @Test
    void executeKeepsCallerContext() throws Exception {
        var timeouter = timeouter(Duration.ofSeconds(5));
        var key = ContextKey.<String>named("key");
        var context = new OpentelemetryContext(Context.root().with(key, "trace"));
        var mdc = new MDC();
        mdc.put0("key", "mdc");
        Principal principal = new Principal() {};

        var seen = ScopedValue.where(OpentelemetryContext.VALUE, context)
            .where(MDC.VALUE, mdc)
            .where(Principal.VALUE, principal)
            .call(() -> timeouter.execute(() -> {
                MDC.put("callee", "value");
                return OpentelemetryContext.VALUE.get().get(key) + " " + MDC.get().values().containsKey("key") + " " + (Principal.current() == principal);
            }));

        assertEquals("trace true true", seen);
        assertFalse(mdc.values().containsKey("callee"), "callee writes must not leak into the caller MDC: an abandoned call would race the caller");
    }

    @Test
    void executeThrowsAtDeadlineWhenWorkIgnoresInterrupt() {
        var timeouter = timeouter(Duration.ofMillis(100));

        assertReleasedAtDeadline(() -> timeouter.execute(() -> {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            long x = 0;
            while (System.nanoTime() < end) {
                x++;
            }
            return x;
        }));
    }

    @Test
    void executeThrowsAtDeadlineWhenWorkJoinsFuture() {
        var timeouter = timeouter(Duration.ofMillis(100));
        var slow = CompletableFuture.supplyAsync(() -> "late", CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS));

        assertReleasedAtDeadline(() -> timeouter.execute(slow::join));
    }

    @Test
    void executeThrowsAtDeadlineAroundRetry() {
        var timeouter = timeouter(Duration.ofMillis(200));
        var retry = new KoraRetry("test", retryConfig(Duration.ofSeconds(1), 3), null, null, NoopRetryTelemetry.INSTANCE);
        var calls = new AtomicInteger();

        assertReleasedAtDeadline(() -> timeouter.execute(() -> retry.retry((ThrowableCallable<Object, RuntimeException>) () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("downstream unavailable");
        })));
    }

    @Test
    void executeThrowsWhenTimedOutAndClearsInterrupt() {
        var timeouter = timeouter(Duration.ofMillis(50));

        assertThrows(TimeoutExhaustedException.class, () -> timeouter.execute(() -> {
            Thread.sleep(5_000);
            return "OK";
        }));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void executeInterruptsWorkWhenCallerInterrupted() throws Exception {
        var timeouter = timeouter(Duration.ofSeconds(30));
        var started = new CountDownLatch(1);
        var workInterrupted = new CountDownLatch(1);
        var caller = Thread.ofVirtual().start(() -> {
            try {
                timeouter.execute(() -> {
                    started.countDown();
                    try {
                        Thread.sleep(20_000);
                    } catch (InterruptedException e) {
                        workInterrupted.countDown();
                    }
                    return null;
                });
            } catch (Throwable ignored) {
            }
        });

        assertTrue(started.await(5, TimeUnit.SECONDS));
        caller.interrupt();
        caller.join(5_000);
        assertTrue(workInterrupted.await(2, TimeUnit.SECONDS), "work keeps running after caller was interrupted");
    }

    private static void assertReleasedAtDeadline(org.junit.jupiter.api.function.Executable call) {
        var started = System.nanoTime();
        assertThrows(TimeoutExhaustedException.class, call);
        var tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue(tookMs < 1000, "caller was blocked for " + tookMs + "ms");
    }

    private static RetryConfig retryConfig(Duration delay, int attempts) {
        return new RetryConfig() {
            @Override
            public Duration delay() {
                return delay;
            }

            @Override
            public @Nullable BackoffConfig backoff() {
                return null;
            }

            @Override
            public @Nullable JitterConfig jitter() {
                return null;
            }

            @Override
            public @Nullable RetryBudgetConfig retryBudget() {
                return null;
            }

            @Override
            public int attempts() {
                return attempts;
            }

            @Override
            public @Nullable TelemetryConfig telemetry() {
                return null;
            }
        };
    }

    private static KoraTimeouter timeouter(Duration duration) {
        return new KoraTimeouter("test", duration, NoopTimeoutTelemetry.INSTANCE, new TimeoutConfig() {
            @Override
            public Duration duration() {
                return duration;
            }

            @Override
            public @Nullable TelemetryConfig telemetry() {
                return null;
            }
        });
    }
}
