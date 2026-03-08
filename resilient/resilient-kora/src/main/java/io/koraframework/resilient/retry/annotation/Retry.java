package io.koraframework.resilient.retry.annotation;

import io.koraframework.common.AopAnnotation;
import io.koraframework.resilient.retry.RetryConfig;
import io.koraframework.resilient.retry.RetryExhaustedException;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link io.koraframework.resilient.retry.Retry} to a specific method
 * When applied to method, method may throw {@link RetryExhaustedException} when all retry attempts are exhausted
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface Retry {

    /**
     * @return the name of Retry config path
     * @see RetryConfig
     */
    String value();
}
