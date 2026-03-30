package io.koraframework.resilient.ratelimiter;

import io.koraframework.resilient.ResilientException;

/**
 * Exception thrown when the rate limit is exceeded for a given {@link RateLimiter}.
 */
public final class RateLimitExceededException extends ResilientException {

    public RateLimitExceededException(String name) {
        super(name, "RateLimiter '" + name + "' rate limit exceeded");
    }
}
