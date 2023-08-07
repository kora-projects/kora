package ru.tinkoff.kora.resilient.retry.annotation;

import ru.tinkoff.kora.common.AopAnnotation;
import ru.tinkoff.kora.resilient.retry.RetryConfig;
import ru.tinkoff.kora.resilient.retry.RetryExhaustedException;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link ru.tinkoff.kora.resilient.retry.Retry} to a specific method
 * When applied to method, method may throw {@link RetryExhaustedException} when all retry attempts are exhausted
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface Retry {

    /**
     * @see RetryConfig
     * @return the name of Retry config path
     */
    String value();
}
