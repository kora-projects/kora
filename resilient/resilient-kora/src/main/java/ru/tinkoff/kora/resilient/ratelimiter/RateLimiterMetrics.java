package ru.tinkoff.kora.resilient.ratelimiter;

/**
 * Metrics interface for {@link RateLimiter} events.
 */
public interface RateLimiterMetrics {

    /**
     * Records a rate limiter acquisition attempt.
     *
     * @param name     the name of the rate limiter
     * @param acquired {@code true} if permit was acquired, {@code false} if rate limit was exceeded
     */
    void recordAcquire(String name, boolean acquired);
}
