package io.koraframework.resilient.circuitbreaker.annotation;

import java.lang.annotation.*;

@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface CircuitBreakerSpec {

    /**
     * @return path for CircuitBreaker config
     */
    String value();
}
