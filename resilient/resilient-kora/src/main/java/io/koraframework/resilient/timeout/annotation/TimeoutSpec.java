package io.koraframework.resilient.timeout.annotation;

import java.lang.annotation.*;

@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface TimeoutSpec {

    /**
     * @return path for Timeout config
     */
    String value();
}
