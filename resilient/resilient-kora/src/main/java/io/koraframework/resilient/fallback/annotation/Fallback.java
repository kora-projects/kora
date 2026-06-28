package io.koraframework.resilient.fallback.annotation;

import io.koraframework.common.AopAnnotation;
import io.koraframework.resilient.fallback.FallbackConfig;

import java.lang.annotation.*;

@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface Fallback {

    /**
     * @see FallbackConfig
     * @return the name of Fallback config path
     */
    String value();

    /**
     * @return fallbackMethod method name to execute
     */
    String method();
}
