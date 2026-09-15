package io.koraframework.resilient.ratelimiter;

import io.koraframework.resilient.ratelimiter.exception.RateLimitExceededException;
import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetry;

/**
 * Base for the local {@link RateLimiter} algorithm implementations. Owns the shared plumbing — enabled short-circuit,
 * telemetry, {@link #acquire()} — and delegates the actual admission decision to {@link #doTryAcquire()}.
 */
abstract class AbstractKoraRateLimiter implements RateLimiter {

    final String name;
    final RateLimiterTelemetry telemetry;
    final boolean enabled;
    final int limitForPeriod;
    final long windowNanos;

    AbstractKoraRateLimiter(String name, RateLimiterConfig config, RateLimiterTelemetry telemetry) {
        this.name = name;
        this.telemetry = telemetry;
        this.enabled = config.enabled();
        this.limitForPeriod = config.limitForPeriod();
        this.windowNanos = config.limitRefreshPeriod().toNanos();
    }

    @Override
    public final boolean tryAcquire() {
        if (!enabled) {
            return true;
        }

        var observation = telemetry.observe();
        boolean acquired = false;
        try {
            acquired = doTryAcquire();
            return acquired;
        } catch (Throwable e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.recordAcquire(acquired);
            observation.end();
        }
    }

    @Override
    public final void acquire() throws RateLimitExceededException {
        if (!tryAcquire()) {
            throw new RateLimitExceededException(name);
        }
    }

    /**
     * Performs one admission decision against the in-memory state.
     *
     * @return {@code true} if a permit is granted, {@code false} if the limit is exceeded
     */
    protected abstract boolean doTryAcquire();
}
