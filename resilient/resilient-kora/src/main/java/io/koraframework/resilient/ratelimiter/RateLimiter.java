package io.koraframework.resilient.ratelimiter;

import io.koraframework.resilient.common.ThrowableCallable;
import io.koraframework.resilient.common.ThrowableRunnable;
import io.koraframework.resilient.ratelimiter.exception.RateLimitExceededException;

/**
 * A {@link RateLimiter} limits the rate of calls to a backend system.
 * The rate is defined as a maximum number of calls ({@link RateLimiterConfig#limitForPeriod()})
 * within a period ({@link RateLimiterConfig#limitRefreshPeriod()}).
 * <p>
 * When the rate limit is exceeded, a {@link RateLimitExceededException} is thrown.
 * <p>
 * Usage via typed style: declare a spec interface with {@code @RateLimiterSpec}
 * and apply {@code @RateLimited} to protected methods.
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
     * Execute runnable with rate limiting protection.
     *
     * @param runnable to execute
     * @throws RateLimitExceededException when rate limit is exceeded
     */
    default <E extends Throwable> void execute(ThrowableRunnable<E> runnable) throws E, RateLimitExceededException {
        acquire();
        runnable.run();
    }
    
    /**
     * Execute supplier with rate limiting protection.
     *
     * @param callable to execute for result
     * @param <T>      type of result
     * @return result from supplier
     * @throws RateLimitExceededException when rate limit is exceeded
     */
    default <T, E extends Throwable> T execute(ThrowableCallable<T, E> callable) throws E, RateLimitExceededException {
        acquire();
        return callable.call();
    }
}
