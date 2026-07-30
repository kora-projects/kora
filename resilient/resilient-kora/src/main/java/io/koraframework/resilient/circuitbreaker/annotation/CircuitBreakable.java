package io.koraframework.resilient.circuitbreaker.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.resilient.circuitbreaker.CircuitBreaker;
import io.koraframework.resilient.circuitbreaker.CircuitBreakerConfig;
import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link CircuitBreaker} to a specific method
 * When applied to method, method may throw {@link CallNotPermittedException} when all CircuitBreaker in OPEN state
 */
@Documented
@AopAnnotation
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface CircuitBreakable {

    /**
     * @see CircuitBreakerConfig
     * @return CircuitBreaker implementation interface
     */
    Class<? extends CircuitBreaker> value();
}
