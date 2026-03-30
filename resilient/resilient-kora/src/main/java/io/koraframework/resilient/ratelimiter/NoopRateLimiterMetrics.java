package io.koraframework.resilient.ratelimiter;

final class NoopRateLimiterMetrics implements RateLimiterMetrics {

    @Override
    public void recordAcquire(String name, boolean acquired) {
        // no-op
    }
}
