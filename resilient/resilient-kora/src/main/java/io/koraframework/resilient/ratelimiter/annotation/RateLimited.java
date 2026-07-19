package io.koraframework.resilient.ratelimiter.annotation;

import io.koraframework.common.annotation.AopAnnotation;

import java.lang.annotation.*;

@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface RateLimited {

    /**
     * @return RateLimiter implementation interface
     */
    Class<? extends io.koraframework.resilient.ratelimiter.RateLimiter> value();
}
