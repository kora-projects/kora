package io.koraframework.resilient.timeout.annotation;

import io.koraframework.common.AopAnnotation;
import io.koraframework.resilient.timeout.TimeoutConfig;
import io.koraframework.resilient.timeout.TimeoutExhaustedException;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link io.koraframework.resilient.timeout.Timeout} to a specific method
 * When applied to method, method may throw {@link TimeoutExhaustedException} when all timeout occured
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface Timeout {

    /**
     * @see TimeoutConfig
     * @return the name of the Timeout config path
     */
    String value();
}
