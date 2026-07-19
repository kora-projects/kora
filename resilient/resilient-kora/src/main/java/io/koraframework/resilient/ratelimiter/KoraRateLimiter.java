package io.koraframework.resilient.ratelimiter;

import io.koraframework.resilient.ratelimiter.exception.RateLimitExceededException;
import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetry;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class KoraRateLimiter implements RateLimiter {

    private final String name;
    private final RateLimiterConfig config;
    private final RateLimiterTelemetry telemetry;
    private final AtomicInteger permissions;
    private final AtomicLong refreshNanos;

    public KoraRateLimiter(String name, RateLimiterConfig config, RateLimiterTelemetry telemetry) {
        this.name = name;
        this.config = config;
        this.telemetry = telemetry;
        this.permissions = new AtomicInteger(config.limitForPeriod());
        this.refreshNanos = new AtomicLong(System.nanoTime() + config.limitRefreshPeriod().toNanos());
    }

    @Override
    public boolean tryAcquire() {
        var observation = telemetry.observe();
        boolean acquired = false;
        try {
            if (!config.enabled()) {
                acquired = true;
                return true;
            }
            refreshIfNeeded();
            while (true) {
                var current = permissions.get();
                if (current <= 0) {
                    return false;
                }
                if (permissions.compareAndSet(current, current - 1)) {
                    acquired = true;
                    return true;
                }
            }
        } catch (Throwable e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.recordAcquire(acquired);
            observation.end();
        }
    }

    @Override
    public void acquire() throws RateLimitExceededException {
        if (!tryAcquire()) {
            throw new RateLimitExceededException(name);
        }
    }

    private void refreshIfNeeded() {
        var now = System.nanoTime();
        var nextRefresh = refreshNanos.get();
        if (now < nextRefresh) {
            return;
        }
        var next = now + config.limitRefreshPeriod().toNanos();
        if (refreshNanos.compareAndSet(nextRefresh, next)) {
            permissions.set(config.limitForPeriod());
        }
    }
}
