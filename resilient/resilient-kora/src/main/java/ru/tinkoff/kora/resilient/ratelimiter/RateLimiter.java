package ru.tinkoff.kora.resilient.ratelimiter;

import java.util.function.Supplier;

/**
 * A {@link RateLimiter} limits the rate of calls to a backend system.
 * The rate is defined as a maximum number of calls ({@link RateLimiterConfig.NamedConfig#limitForPeriod()})
 * within a period ({@link RateLimiterConfig.NamedConfig#limitRefreshPeriod()}).
 * <p>
 * When the rate limit is exceeded, a {@link RateLimitExceededException} is thrown.
 * <p>
 * Usage via annotation: {@link ru.tinkoff.kora.resilient.ratelimiter.annotation.RateLimit}
 * Usage via imperative style: inject {@link RateLimiterManager} and call {@link RateLimiterManager#get(String)}
 */
public interface RateLimiter {

    /**
     * Try to acquire a rate limit permit.
     *
     * @return {@code true} if permit was acquired, {@code false} if rate limit is exceeded
     */
    boolean tryAcquire();

    /**
     * Acquire a rate limit permit, throwing an exception if rate limit is exceeded.
     *
     * @throws RateLimitExceededException when rate limit is exceeded
     */
    void acquire() throws RateLimitExceededException;

    /**
     * Execute supplier with rate limiting protection.
     *
     * @param supplier to execute for result
     * @param <T>      type of result
     * @return result from supplier
     * @throws RateLimitExceededException when rate limit is exceeded
     */
    default <T> T execute(Supplier<T> supplier) throws RateLimitExceededException {
        acquire();
        return supplier.get();
    }

    /**
     * Execute runnable with rate limiting protection.
     *
     * @param runnable to execute
     * @throws RateLimitExceededException when rate limit is exceeded
     */
    default void execute(Runnable runnable) throws RateLimitExceededException {
        acquire();
        runnable.run();
    }
}
