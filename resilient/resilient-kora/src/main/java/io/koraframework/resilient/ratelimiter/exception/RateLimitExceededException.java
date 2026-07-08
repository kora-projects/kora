package io.koraframework.resilient.ratelimiter.exception;

import io.koraframework.resilient.exception.ResilientException;
import io.koraframework.resilient.ratelimiter.RateLimiter;

/**
 * Exception thrown when the rate limit is exceeded for a given {@link RateLimiter}.
 */
public final class RateLimitExceededException extends ResilientException {

    public RateLimitExceededException(String name) {
        super(name, "RateLimiter '" + name + "' rate limit exceeded");
    }
}
