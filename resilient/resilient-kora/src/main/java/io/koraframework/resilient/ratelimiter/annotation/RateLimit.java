package io.koraframework.resilient.ratelimiter.annotation;

import io.koraframework.common.AopAnnotation;
import io.koraframework.resilient.ratelimiter.RateLimitExceededException;
import io.koraframework.resilient.ratelimiter.RateLimiterConfig;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link io.koraframework.resilient.ratelimiter.RateLimiter} to a specific method.
 * When applied to method, method may throw {@link RateLimitExceededException} when rate limit is exceeded.
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface RateLimit {

    /**
     * @see RateLimiterConfig
     * @return the name of RateLimiter config path
     */
    String value();
}
