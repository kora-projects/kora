package ru.tinkoff.kora.resilient.retry.annotation;

import ru.tinkoff.kora.common.AopAnnotation;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link ru.tinkoff.kora.resilient.retry.Retrier} to a specific method
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
