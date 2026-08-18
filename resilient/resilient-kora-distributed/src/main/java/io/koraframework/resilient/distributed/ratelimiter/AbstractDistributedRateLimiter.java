package io.koraframework.resilient.distributed.ratelimiter;

import io.koraframework.resilient.ratelimiter.RateLimiter;
import io.koraframework.resilient.ratelimiter.exception.RateLimitExceededException;
import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetry;
import org.jspecify.annotations.Nullable;

/**
 * Base class for distributed {@link RateLimiter} implementations bound to a single runtime key. It owns the shared
 * plumbing — window parameters, telemetry, {@link #acquire()} — and delegates the actual counting decision to
 * {@link #doTryAcquire()}.
 */
abstract class AbstractDistributedRateLimiter implements RateLimiter {

    final String name;
    final String keyBase;
    final DistributedRateLimiterClient client;
    final boolean enabled;
    final int limitForPeriod;
    final long windowMillis;

    @Nullable
    private final RateLimiterTelemetry telemetry;

    AbstractDistributedRateLimiter(String name,
                                   String keyBase,
                                   DistributedRateLimiterConfig config,
                                   DistributedRateLimiterClient client,
                                   @Nullable RateLimiterTelemetry telemetry) {
        this.name = name;
        this.keyBase = keyBase;
        this.client = client;
        this.telemetry = telemetry;
        this.enabled = config.enabled();
        this.limitForPeriod = config.limitForPeriod();
        this.windowMillis = config.limitRefreshPeriod().toMillis();
    }

    @Override
    public final boolean tryAcquire() {
        if (!enabled) {
            return true;
        }

        var observation = telemetry == null ? null : telemetry.observe();
        boolean acquired = false;
        try {
            acquired = doTryAcquire();
            return acquired;
        } catch (Throwable e) {
            if (observation != null) {
                observation.observeError(e);
            }
            throw e;
        } finally {
            if (observation != null) {
                observation.recordAcquire(acquired);
                observation.end();
            }
        }
    }

    @Override
    public final void acquire() throws RateLimitExceededException {
        if (!tryAcquire()) {
            throw new RateLimitExceededException(name);
        }
    }

    /**
     * Performs one rate limiting decision against the backend.
     *
     * @return {@code true} if a permit is granted, {@code false} if the limit is exceeded
     */
    protected abstract boolean doTryAcquire();
}
