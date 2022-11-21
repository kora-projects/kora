package ru.tinkoff.kora.resilient.circuitbreaker.annotation;

import ru.tinkoff.kora.common.AopAnnotation;
import ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker} to a specific method
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface CircuitBreaker {

    /**
     * @return the name of the circuitBreaker breaker and part of the config path
     */
    String value();

    /**
     * Method must have the same signature as the original method
     *
     * @return fallbackMethod method name to use when {@link CallNotPermittedException} occur
     */
    String fallbackMethod() default "";
}
