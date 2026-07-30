package io.koraframework.resilient.retry.annotation;

import io.koraframework.common.annotation.AopAnnotation;

import java.lang.annotation.*;

@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface Retryable {

    /**
     * @return Retry implementation interface
     */
    Class<? extends io.koraframework.resilient.retry.Retry> value();
}
