package io.koraframework.resilient.timeout.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.resilient.timeout.Timeouter;

import java.lang.annotation.*;

@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface Timeout {

    /**
     * @return Timeout implementation interface
     */
    Class<? extends Timeouter> value();
}
