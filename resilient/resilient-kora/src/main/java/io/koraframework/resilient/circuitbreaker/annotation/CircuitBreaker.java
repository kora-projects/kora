package io.koraframework.resilient.circuitbreaker.annotation;

import io.koraframework.common.AopAnnotation;
import io.koraframework.resilient.circuitbreaker.CallNotPermittedException;
import io.koraframework.resilient.circuitbreaker.CircuitBreakerConfig;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link io.koraframework.resilient.circuitbreaker.CircuitBreaker} to a specific method
 * When applied to method, method may throw {@link CallNotPermittedException} when all CircuitBreaker in OPEN state
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface CircuitBreaker {

    /**
     * @see CircuitBreakerConfig
     * @return the name of CircuitBreaker config path
     */
    String value();
}
