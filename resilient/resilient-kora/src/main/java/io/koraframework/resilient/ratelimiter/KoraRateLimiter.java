package io.koraframework.resilient.ratelimiter;

import io.koraframework.resilient.ratelimiter.exception.RateLimitExceededException;
import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetry;

/**
 * Local {@link RateLimiter} that selects a concrete algorithm from {@link RateLimiterConfig#type()} and delegates to it,
 * the same way {@link io.koraframework.resilient.circuitbreaker.KoraCircuitBreaker} switches over its implementations.
 *
 * @see FixedWindowKoraRateLimiter
 * @see TokenBucketKoraRateLimiter
 */
public class KoraRateLimiter implements RateLimiter {

    private final RateLimiter delegate;

    public KoraRateLimiter(String name, RateLimiterConfig config, RateLimiterTelemetry telemetry) {
        this.delegate = switch (config.type()) {
            case FIXED_WINDOW -> new FixedWindowKoraRateLimiter(name, config, telemetry);
            case TOKEN_BUCKET -> new TokenBucketKoraRateLimiter(name, config, telemetry);
        };
    }

    @Override
    public boolean tryAcquire() {
        return delegate.tryAcquire();
    }

    @Override
    public void acquire() throws RateLimitExceededException {
        delegate.acquire();
    }

    RateLimiter delegate() {
        return this.delegate;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
