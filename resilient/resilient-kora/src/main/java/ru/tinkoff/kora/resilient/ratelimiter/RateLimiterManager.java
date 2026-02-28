package ru.tinkoff.kora.resilient.ratelimiter;

/**
 * Manager that provides named {@link RateLimiter} instances.
 * Instances are created lazily and cached by name.
 */
public interface RateLimiterManager {

    /**
     * @param name the name of the rate limiter, used to look up configuration
     * @return a {@link RateLimiter} instance for the given name
     */
    RateLimiter get(String name);
}
