package ru.tinkoff.kora.resilient.retry.annotation;

import ru.tinkoff.kora.common.AopAnnotation;
import ru.tinkoff.kora.resilient.retry.Retrier;
import ru.tinkoff.kora.resilient.retry.RetryAttemptException;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link Retrier} to a specific method
 * When applied to method, method may throw {@link RetryAttemptException} when all retry attempts are exhausted
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface Retryable {

    /**
     * @return the name of the retryable and part of the config path
     */
    String value();
}
