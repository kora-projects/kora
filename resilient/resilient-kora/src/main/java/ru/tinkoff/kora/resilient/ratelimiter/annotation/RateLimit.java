package ru.tinkoff.kora.resilient.ratelimiter.annotation;

import ru.tinkoff.kora.common.AopAnnotation;
import ru.tinkoff.kora.resilient.ratelimiter.RateLimitExceededException;
import ru.tinkoff.kora.resilient.ratelimiter.RateLimiterConfig;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link ru.tinkoff.kora.resilient.ratelimiter.RateLimiter} to a specific method.
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
