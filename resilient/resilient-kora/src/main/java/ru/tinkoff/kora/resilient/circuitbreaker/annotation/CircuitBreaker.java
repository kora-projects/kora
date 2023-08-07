package ru.tinkoff.kora.resilient.circuitbreaker.annotation;

import ru.tinkoff.kora.common.AopAnnotation;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerConfig;
import ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker} to a specific method
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
