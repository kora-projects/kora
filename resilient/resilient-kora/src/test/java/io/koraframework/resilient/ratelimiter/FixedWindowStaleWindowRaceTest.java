package io.koraframework.resilient.ratelimiter;

import io.koraframework.resilient.ratelimiter.telemetry.impl.NoopRateLimiterTelemetry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class FixedWindowStaleWindowRaceTest extends Assertions {

    @Test
    void grantsInOneWindowNeverExceedLimit() throws Exception {
        int limit = 3;
        long windowNanos = Duration.ofMillis(2).toNanos();
        var config = new RateLimiterConfig() {
            public RateLimiterType type() { return RateLimiterType.FIXED_WINDOW; }
            public int limitForPeriod() { return limit; }
            public Duration limitRefreshPeriod() { return Duration.ofNanos(windowNanos); }
            public @Nullable TelemetryConfig telemetry() { return null; }
        };
        var rl = new FixedWindowKoraRateLimiter("rl", config, NoopRateLimiterTelemetry.INSTANCE);
        var f = FixedWindowKoraRateLimiter.class.getDeclaredField("startedNanos");
        f.setAccessible(true);
        long started = (long) f.get(rl);

        var perWindow = new ConcurrentHashMap<Long, AtomicInteger>();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        var threads = new ArrayList<Thread>();
        for (int t = 0; t < 64; t++) {
            threads.add(Thread.ofPlatform().start(() -> {
                while (System.nanoTime() < deadline) {
                    long t0 = System.nanoTime();
                    boolean ok = rl.tryAcquire();
                    long t1 = System.nanoTime();
                    long w0 = (t0 - started) / windowNanos;
                    long w1 = (t1 - started) / windowNanos;
                    if (ok && w0 == w1) {
                        perWindow.computeIfAbsent(w0, k -> new AtomicInteger()).incrementAndGet();
                    }
                }
            }));
        }
        for (var th : threads) th.join();

        var over = perWindow.entrySet().stream().filter(e -> e.getValue().get() > limit).toList();
        System.out.println("windows=" + perWindow.size() + " overLimit=" + over.size() + " sample=" + over.stream().limit(10).toList());
        assertTrue(over.isEmpty(), "windows granting more than limitForPeriod=" + limit + ": " + over.size() + " e.g. " + over.stream().limit(5).toList());
    }
}
