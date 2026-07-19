package io.koraframework.resilient.ratelimiter.annotation;

import java.lang.annotation.*;

@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface RateLimiterSpec {

    /**
     * @return path for RateLimiter config
     */
    String value();
}
